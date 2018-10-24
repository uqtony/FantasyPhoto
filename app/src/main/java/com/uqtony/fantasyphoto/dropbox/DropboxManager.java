package com.uqtony.fantasyphoto.dropbox;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;

import com.dropbox.core.DbxException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.sharing.CreateSharedLinkWithSettingsErrorException;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;

import java.text.DateFormat;

import static android.content.Context.MODE_PRIVATE;


/**
 * Base class for Activities that require auth tokens
 * Will redirect to auth flow if needed
 */
public class DropboxManager {
    protected static final String ACCESS_TOKEN = "5w5Ri2g77eAAAAAAAAAAMTyM0Frn2L4_M2ivrvrcWzN6u9yj4mjVBWbOeDwAvvK4";
    private static final String TAG = "DropboxManager";
    private static final Object lock = new Object();
    private static DropboxManager _inst = null;

    private Context context;

    public interface UploadListener{
        public void onUploadComplete(final String dropboxFilePath);
        public void onError(final Exception e);
    }

    public static DropboxManager getInst(Context context) {
        synchronized (lock){
            if (_inst == null)
                _inst = new DropboxManager(context) ;
        }
        return _inst;
    }

    private DropboxManager(Context context){
        this.context = context;
        init();
    }

    protected void init() {

        SharedPreferences prefs = context.getSharedPreferences("dropbox-sample", MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        accessToken = ACCESS_TOKEN;
        if (accessToken == null) {
            accessToken = Auth.getOAuth2Token();
            if (accessToken != null) {
                prefs.edit().putString("access-token", accessToken).apply();
                initAndLoadData(accessToken);
            }
        } else {
            initAndLoadData(accessToken);
        }

        String uid = Auth.getUid();
        String storedUid = prefs.getString("user-id", null);
        if (uid != null && !uid.equals(storedUid)) {
            prefs.edit().putString("user-id", uid).apply();
        }
    }

    private void initAndLoadData(String accessToken) {
        DropboxClientFactory.init(accessToken);
        PicassoClient.init(context, DropboxClientFactory.getClient());
        loadData();
    }

    protected void loadData(){

    }

    protected boolean hasToken() {
        SharedPreferences prefs = context.getSharedPreferences("dropbox-sample", MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        accessToken = ACCESS_TOKEN;
        return accessToken != null;
    }

    public String getDropboxSharedLink(String dropboxFilePath){
        String link = null;
        try {

            SharedLinkMetadata sharedLinkMetadata;
            sharedLinkMetadata= DropboxClientFactory.getClient().sharing().createSharedLinkWithSettings(dropboxFilePath, null);
            link = sharedLinkMetadata.getUrl();
            Log.i(TAG, "Shared url="+ link);
        }
        catch (CreateSharedLinkWithSettingsErrorException e)
        {
            e.printStackTrace();
        }
        catch (DbxException e){
            e.printStackTrace();
        }

        return link;
    }

    public void uploadFile(String fileUri, final UploadListener listener) {

        new UploadFileTask(context, DropboxClientFactory.getClient(), new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {

                String message = result.getName() + " size " + result.getSize() + " modified " +
                        DateFormat.getDateTimeInstance().format(result.getClientModified());
                final String filePath = result.getPathLower();
                if (listener != null){
                    listener.onUploadComplete(filePath);
                }
                Log.i(TAG, "Upload file completed, "+ message);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to upload file.", e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        }).execute(fileUri, "");
    }
}
