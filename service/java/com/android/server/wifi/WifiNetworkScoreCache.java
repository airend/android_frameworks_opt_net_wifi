/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.Manifest.permission;
import android.content.Context;
import android.net.INetworkScoreCache;
import android.net.NetworkKey;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiNetworkScoreCache extends INetworkScoreCache.Stub
 {

    private static String TAG = "WifiNetworkScoreCache";
    private boolean DBG = true;
    private final Context mContext;
    private final Map<String, ScoredNetwork> mNetworkCache;

    public WifiNetworkScoreCache(Context context) {
        mContext = context;
        mNetworkCache = new HashMap<String, ScoredNetwork>();
    }


     //void updateScores(in List<ScoredNetwork> networks);

     @Override public final void updateScores(List<android.net.ScoredNetwork> networks) {
        if (networks == null) {
            return;
        }
        Log.e(TAG, "updateScores list size=" + networks.size());

        synchronized(mNetworkCache) {
            for (ScoredNetwork network : networks) {
                String networkKey = buildNetworkKey(network);
                if (networkKey == null) continue;
                mNetworkCache.put(networkKey, network);
            }
        }
     }

     @Override public final void clearScores() {
         synchronized(mNetworkCache) {
             mNetworkCache.clear();
         }
     }


    public int getNetworkScore(ScanResult result) {

        int score = -1;

        String key = buildNetworkKey(result);
        if (key == null) return score;

        //find it
        synchronized(mNetworkCache) {
            ScoredNetwork network = mNetworkCache.get(key);
            if (network != null && network.rssiCurve != null) {
                score = network.rssiCurve.lookupScore(result.level);
                if (DBG) {
                    Log.e(TAG, "getNetworkScore found Herrevad network" + key
                            + " score " + Integer.toString(score)
                            + " RSSI " + result.level);
                }
            }
        }
        return score;
    }

    private String buildNetworkKey(ScoredNetwork network) {
        if (network.networkKey == null) return null;
        if (network.networkKey.wifiKey == null) return null;
        if (network.networkKey.type == NetworkKey.TYPE_WIFI) {
            String key = network.networkKey.wifiKey.ssid;
            if (key == null) return null;
            if (network.networkKey.wifiKey.bssid != null) {
                key = key + network.networkKey.wifiKey.bssid;
            }
            return key;
        }
        return null;
    }

    private String buildNetworkKey(ScanResult result) {
        String key = result.SSID;
        if (key == null) return null;
        if (result.BSSID != null) {
            key = key + result.BSSID;
        }
        return key;
    }

    @Override protected final void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingOrSelfPermission(permission.DUMP, TAG);
        writer.println("WifiNetworkScoreCache");
        writer.println("  All score curves:");
        for (Map.Entry<String, ScoredNetwork> entry : mNetworkCache.entrySet()) {
            writer.println("    " + entry.getKey() + ": " + entry.getValue().rssiCurve);
        }
        writer.println("  Current network scores:");
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        for (ScanResult scanResult : wifiManager.getScanResults()) {
            writer.println("    " + buildNetworkKey(scanResult) + ": " + getNetworkScore(scanResult));
        }
    }

}