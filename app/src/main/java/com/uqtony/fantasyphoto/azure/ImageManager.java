/**----------------------------------------------------------------------------------
* Microsoft Developer & Platform Evangelism
*
* Copyright (c) Microsoft Corporation. All rights reserved.
*
* THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, 
* EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES 
* OF MERCHANTABILITY AND/OR FITNESS FOR A PARTICULAR PURPOSE.
*----------------------------------------------------------------------------------
* The example companies, organizations, products, domain names,	
* e-mail addresses, logos, people, places, and events depicted
* herein are fictitious.  No association with any real company,
* organization, product, domain name, email address, logo, person,
* places, or events is intended or should be inferred.
*----------------------------------------------------------------------------------
**/

package com.uqtony.fantasyphoto.azure;

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.BlobContainerPermissions;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class ImageManager {
    /*
    **Only use Shared Key authentication for testing purposes!** 
    Your account name and account key, which give full read/write access to the associated Storage account, 
    will be distributed to every person that downloads your app. 
    This is **not** a good practice as you risk having your key compromised by untrusted clients. 
    Please consult following documents to understand and use Shared Access Signatures instead. 
    https://docs.microsoft.com/en-us/rest/api/storageservices/delegating-access-with-a-shared-access-signature 
    and https://docs.microsoft.com/en-us/azure/storage/common/storage-dotnet-shared-access-signature-part-1 
    */
    // East Asia
    public static final String storageConnectionString = "DefaultEndpointsProtocol=https;"
            + "AccountName=bvuqtony;"
            + "AccountKey=BQRfc6EpBsPXlFcSNR732ATjHO5vxUgNBDH3rVbfukzIINbH7i2bcgvpIbEbK3k+sBkcSgfME2U3iEqcbsQ+Ow==";
    // US West Coast
//    public static final String storageConnectionString = "DefaultEndpointsProtocol=https;"
//            + "AccountName=bvuqtonyuswc;"
//            + "AccountKey=u0Qgt0vOttZZXeeB7C2Rc2jwQTW6maw9oTHfDvGSkXLAwuhh8vsqcOg1J3U9RCKUr9L+iY7HqRKpZrLGEWFOHQ==";

    private static CloudBlobContainer getContainer() throws Exception {
        // Retrieve storage account from connection-string.

        CloudStorageAccount storageAccount = CloudStorageAccount
                .parse(storageConnectionString);

        // Create the blob client.
        CloudBlobClient blobClient = storageAccount.createCloudBlobClient();

        // Get a reference to a container.
        // The container name must be lower case
        CloudBlobContainer container = blobClient.getContainerReference("images");
        if (!container.exists()) {
            // Create the container if it does not exist
            container.createIfNotExists();

            // Make the container public
            // Create a permissions object
            BlobContainerPermissions containerPermissions = new BlobContainerPermissions();

            // Include public access in the permissions object
            containerPermissions
                    .setPublicAccess(BlobContainerPublicAccessType.CONTAINER);

            // Set the permissions on the container
            container.uploadPermissions(containerPermissions);
        }
        return container;
    }

    public static String UploadImage(String fileUrl) throws Exception
    {
        Uri uri = Uri.parse(fileUrl);
        return UploadImage(new File(uri.getPath()));
    }

    public static String UploadImage(File image) throws Exception {
        if (!image.exists() || image.isDirectory())
            throw new Exception("File not exists or is directory");
        String name = image.getName();
        FileInputStream fileInputStream = new FileInputStream(image);
        CloudBlobContainer container = getContainer();

        container.createIfNotExists();
        String remoteFileName = name;
        SimpleDateFormat formatter = new SimpleDateFormat("dd_MMM_yyyy _hh_mm_ss_");
        String now = formatter.format(new Date());
        remoteFileName = Build.SERIAL+now+remoteFileName;

        CloudBlockBlob imageBlob = container.getBlockBlobReference(remoteFileName);
        imageBlob.upload(fileInputStream, image.length());
        URI uri = imageBlob.getUri();

        Log.d("ImageManager", "upload completed, uri="+uri.toString());
        return uri.toString();
    }

    public static String UploadImage(InputStream image, int imageLength) throws Exception {

        CloudBlobContainer container = getContainer();

        container.createIfNotExists();

        String imageName = randomString(10);

        CloudBlockBlob imageBlob = container.getBlockBlobReference(imageName);
        imageBlob.upload(image, imageLength);
        URI uri = imageBlob.getUri();

        Log.d("ImageManager", "upload completed, uri="+uri.toString());
        return imageName;

    }

    public static String[] ListImages() throws Exception{
        CloudBlobContainer container = getContainer();

        Iterable<ListBlobItem> blobs = container.listBlobs();

        LinkedList<String> blobNames = new LinkedList<>();
        for(ListBlobItem blob: blobs) {
            blobNames.add(((CloudBlockBlob) blob).getName());
        }

        return blobNames.toArray(new String[blobNames.size()]);
    }

    public static void GetImage(String name, OutputStream imageStream, long imageLength) throws Exception {
        CloudBlobContainer container = getContainer();

        CloudBlockBlob blob = container.getBlockBlobReference(name);

        if(blob.exists()){
            blob.downloadAttributes();

            imageLength = blob.getProperties().getLength();

            blob.download(imageStream);
        }
    }

    static final String validChars = "abcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    static String randomString( int len ){
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( validChars.charAt( rnd.nextInt(validChars.length()) ) );
        return sb.toString();
    }

}
