/*
 * <!--
 *   ~ Copyright (c) 2017. ThanksMister LLC
 *   ~
 *   ~ Licensed under the Apache License, Version 2.0 (the "License");
 *   ~ you may not use this file except in compliance with the License. 
 *   ~ You may obtain a copy of the License at
 *   ~
 *   ~ http://www.apache.org/licenses/LICENSE-2.0
 *   ~
 *   ~ Unless required by applicable law or agreed to in writing, software distributed 
 *   ~ under the License is distributed on an "AS IS" BASIS, 
 *   ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *   ~ See the License for the specific language governing permissions and 
 *   ~ limitations under the License.
 *   -->
 */

package com.thanksmister.androidthings.iot.alarmpanel.data.stores;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.thanksmister.androidthings.iot.alarmpanel.data.database.Db;
import com.thanksmister.androidthings.iot.alarmpanel.data.database.model.FeedDataModel;
import com.thanksmister.androidthings.iot.alarmpanel.network.model.FeedData;
import com.thanksmister.androidthings.iot.alarmpanel.network.sync.SyncProvider;

import java.util.ArrayList;
import java.util.List;

import dpreference.DPreference;

/**
 * Place to store global application values as well as get references to local and remote data stores
 */
public class StoreManager {

    private static final long PREFS_ALL_DATA_SYNC_FREQUENCY = 24 * 60 * 60 * 1000;  // 12 hours in milliseconds
    private static final String PREFS_ALL_DATA_LAST_SYNC_TIME = "pref_all_data_last_sync_time";
    
    private final DPreference sharedPreferences;
    private final ContentResolver contentResolver;
    private final Context context;

    public StoreManager(Context context, ContentResolver contentResolver, DPreference sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.contentResolver = contentResolver;
        this.context = context;
    }
    
    public boolean isAllDataSyncNeeded() {
        synchronized (this) {
            return System.currentTimeMillis() >= getAllDataLastSyncTime() + PREFS_ALL_DATA_SYNC_FREQUENCY;
        }
    }

    public long getAllDataLastSyncTime() {
        return sharedPreferences.getPrefLong(PREFS_ALL_DATA_LAST_SYNC_TIME, -1);
    }

    public void setAllDataNextSyncTime() {
        long expire = System.currentTimeMillis() + PREFS_ALL_DATA_SYNC_FREQUENCY;
        this.sharedPreferences.setPrefLong(PREFS_ALL_DATA_LAST_SYNC_TIME, expire);
    }
    
    /**
     * Returns the <code>FeedDataModel</code> list from the database
     * @return
     */
    public List<FeedDataModel> getFeedDataList() {
        List<FeedDataModel> modelList = new ArrayList<>();
        Cursor cursor = contentResolver.query(SyncProvider.FEED_DATA_TABLE_URI, null, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                FeedDataModel feedDataModel = FeedDataModel.getModel(cursor);
                modelList.add(feedDataModel);
            }
            cursor.close();
        }
        return modelList;
    }

    // TODO maybe need bulk insert here
    /**
     * Save the list of <code>FeedData</code> to the database
     */
    public void updateFeedDataList(List<FeedData> feedDataList) {
        for(FeedData feedData : feedDataList) {
            updateFeedData(feedData);
        }
    }

    /**
     * Save the current <code>FeedData</code> to the database
     */
    public void updateFeedData(FeedData feedData) {
        synchronized( this ) {
            Cursor cursor = contentResolver.query(SyncProvider.FEED_DATA_TABLE_URI, FeedDataModel.COLUMN_NAMES, FeedDataModel.DATA_ID + " = ?", new String[]{String.valueOf(feedData.getId())}, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    long id = Db.getLong(cursor, FeedDataModel._ID);
                    contentResolver.update(SyncProvider.FEED_DATA_TABLE_URI, FeedDataModel.createBuilder(feedData).build(), FeedDataModel._ID + " = ?", new String[]{String.valueOf(id)});
                }
            } else {
                contentResolver.insert(SyncProvider.FEED_DATA_TABLE_URI, FeedDataModel.createBuilder(feedData).build());
                contentResolver.notifyChange(SyncProvider.FEED_DATA_TABLE_URI, null);
            }
        }
    }

    public void reset() {
        sharedPreferences.removePreference(PREFS_ALL_DATA_LAST_SYNC_TIME);
        contentResolver.delete(SyncProvider.UPDATES_TABLE_URI, null, null);
        contentResolver.delete(SyncProvider.FEED_DATA_TABLE_URI, null, null);
    }

    /**
     * Convenience method for clearing a table based on Uri
     * @param uri
     */
    public void resetTable(Uri uri) {
        contentResolver.delete(uri, null, null);
    }
}