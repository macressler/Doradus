/*
 * Copyright (C) 2014 Dell, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dell.doradus.service.db.s3;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.client.CredentialsProvider;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3Service;
import org.jets3t.service.ServiceException;
import org.jets3t.service.StorageObjectsChunk;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.security.AWSCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dell.doradus.common.UserDefinition;
import com.dell.doradus.core.ServerParams;
import com.dell.doradus.service.db.ColumnDelete;
import com.dell.doradus.service.db.ColumnUpdate;
import com.dell.doradus.service.db.DBService;
import com.dell.doradus.service.db.DBTransaction;
import com.dell.doradus.service.db.DColumn;
import com.dell.doradus.service.db.RowDelete;
import com.dell.doradus.service.db.Tenant;

public class AmazonS3Service extends DBService {
    public static final int CHUNK_SIZE = 2048;
    protected final Logger m_logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private static final AmazonS3Service INSTANCE = new AmazonS3Service();
    private S3Service m_s3service;
    private long m_totalRequests = 0;
    public String BUCKET;
    
    
    private AmazonS3Service() { }

    public static AmazonS3Service instance() { return INSTANCE; }
    
    @Override public void initService() {
        String accessKey = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-access-key");
        String secretKey = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-secret-key");
        BUCKET = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-bucket");

        AWSCredentials awsCredentials = new AWSCredentials(accessKey, secretKey); 
        S3Service s3service = new RestS3Service(awsCredentials);
        Jets3tProperties props = s3service.getJetS3tProperties();
        
        String port = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-endpoint-http-port");
        String httpsOnly = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-https-only");
        String endpoint = ServerParams.instance().getModuleParamString("AmazonS3Service", "s3-endpoint");
        if(port != null) props.setProperty("s3service.s3-endpoint-http-port", port);
        if(httpsOnly != null) props.setProperty("s3service.https-only", httpsOnly);
        if(endpoint != null) props.setProperty("s3service.s3-endpoint", endpoint);
        //props.setProperty("s3service.s3-endpoint-http-port", "10453");
        //props.setProperty("s3service.https-only", "false");
        //props.setProperty("s3service.s3-endpoint", "localhost");
        props.setProperty("s3service.disable-dns-buckets", "true");
        CredentialsProvider credentials = s3service.getCredentialsProvider();
        m_s3service = new RestS3Service(awsCredentials, "Doradus", credentials, props);
    }

    @Override public void startService() { }
    
    @Override public void stopService() { }
    
    @Override public void createTenant(Tenant tenant, Map<String, String> options) {
        //?? how to create tenant?
    }

    @Override public void modifyTenant(Tenant tenant, Map<String, String> options) {
        throw new RuntimeException("Not implemented");
    }

    private void inc() {
        m_totalRequests++;
        if(m_totalRequests % 10000 == 0) {
            m_logger.info("Requests: {}", "" + (m_totalRequests / 1000) + "k");
        }
    }
    
    private void deleteAll(String path) {
        try {
            String priorLastKey = null;
            while(true) {
                StorageObjectsChunk chunk = m_s3service.listObjectsChunked(BUCKET, path, "?", CHUNK_SIZE, priorLastKey);
                inc();
                StorageObject[] objects = chunk.getObjects();
                if(objects.length == 0) break;
                //m_logger.info("deleting {} objects from {}", objects.length, path);
                // does not work!!!
                //String[] names = new String[objects.length];
                //for(int i = 0; i < objects.length; i++) {
                //    names[i] = objects[i].getKey();
                //}
                //m_s3service.deleteMultipleObjects(BUCKET, names);
                
                for(StorageObject object: objects) {
                    String key = object.getKey();
                    m_s3service.deleteObject(BUCKET, key);
                }
                
                if(chunk.isListingComplete()) break;
                priorLastKey = chunk.getPriorLastKey();
            }
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> listAll(String path) {
        try {
            List<String> result = new ArrayList<>();
            String priorLastKey = null;
            while(true) {
                StorageObjectsChunk chunk = m_s3service.listObjectsChunked(BUCKET, path, "/", CHUNK_SIZE, priorLastKey);
                inc();
                StorageObject[] objects = chunk.getObjects();
                for(int i = 0; i < objects.length; i++) {
                    String key = objects[i].getKey();
                    if(key.endsWith("/")) key = key.substring(0, key.length() - 1);
                    key = key.substring(path.length(), key.length());
                    result.add(key);
                }
                if(chunk.isListingComplete()) break;
                priorLastKey = chunk.getPriorLastKey();
            }
            return result;
       } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
    }
    
    private byte[] get(String path) {
        try {
            S3Object obj = m_s3service.getObject(BUCKET, path);
            inc();
            if(obj == null) return null;
            int len = (int)obj.getContentLength();
            byte[] data = new byte[len];
            int start = 0;
            InputStream is = obj.getDataInputStream();
            while(start < len) {
                start += is.read(data, start, len - start);
            }
            is.close();
            return data;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void put(String path, byte[] value) {
        try {
            S3Object obj = new S3Object(path, value);
            m_s3service.putObject(BUCKET, obj);
            inc();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private void delete(String path) {
        try {
            m_s3service.deleteObject(BUCKET, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override public void dropTenant(Tenant tenant) {
        deleteAll(tenant.getKeyspace());
    }
    
    
    @Override public void addUsers(Tenant tenant, Iterable<UserDefinition> users) {
        throw new RuntimeException("This method is not supported");
    }
    
    @Override public void modifyUsers(Tenant tenant, Iterable<UserDefinition> users) {
        throw new RuntimeException("This method is not supported");
    }
    
    @Override public void deleteUsers(Tenant tenant, Iterable<UserDefinition> users) {
        throw new RuntimeException("This method is not supported");
    }
    
    @Override public Collection<Tenant> getTenants() {
        List<Tenant> tenants = new ArrayList<>();
        List<String> keyspaces = listAll("/");
        for(String keyspace: keyspaces) {
            tenants.add(new Tenant(keyspace));
        }
        return tenants;
    }

    @Override public void createStoreIfAbsent(Tenant tenant, String storeName, boolean bBinaryValues) {
        //???
    }
    
    @Override public void deleteStoreIfPresent(Tenant tenant, String storeName) {
        deleteAll(tenant.getKeyspace() + "/" + storeName);
    }
    
    public String encode(String name) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if(c != '_' && Character.isLetterOrDigit(c)) sb.append(c);
            else {
                String esc = String.format("_%02x", (int)c);
                sb.append(esc);
            }
        }
        return sb.toString();
    }
    
    public String decode(String name) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if(c != '_') sb.append(c);
            else {
                c = (char)Integer.parseInt(name.substring(i + 1, i + 3), 16);
                sb.append(c);
                i += 2;
            }
        }
        return sb.toString();
    }
    
    @Override public void commit(DBTransaction dbTran) {
        try {
        	String keyspace = dbTran.getNamespace();
            //1. update
            for(ColumnUpdate mutation: dbTran.getColumnUpdates()) {
                String store = mutation.getStoreName();
                String row = mutation.getRowKey();
                DColumn c = mutation.getColumn();
                String column = c.getName();
                byte[] value = c.getRawValue();
                String path = keyspace + "/" + store + "/" + encode(row) + "/" + encode(column);
                put(path, value);
            }
            //2. delete columns
            for(ColumnDelete mutation: dbTran.getColumnDeletes()) {
                String store = mutation.getStoreName();
                String row = mutation.getRowKey();
                String column = mutation.getColumnName();
                String path = keyspace + "/" + store + "/" + encode(row) + "/" + encode(column);
                delete(path);
            }
            //3. delete rows
            for(RowDelete mutation: dbTran.getRowDeletes()) {
                String store = mutation.getStoreName();
                String row = mutation.getRowKey();
                String path = keyspace + "/" + store + "/" + encode(row);
                deleteAll(path);
            }
        }catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public List<DColumn> getColumns(String namespace, String storeName,
            String rowKey, String startColumn, String endColumn, int count) {
        ArrayList<DColumn> list = new ArrayList<>();
        String path = namespace + "/" + storeName + "/" + encode(rowKey) + "/";
        List<String> keys = listAll(path);
        for(String key: keys) {
            String name = decode(key);
            if(startColumn != null && startColumn.compareTo(name) > 0) continue;
            if(endColumn != null && endColumn.compareTo(name) <= 0) continue;
            byte[] value = get(path + key);
            if(value == null) continue;
            list.add(new DColumn(name, value));
        }
        Collections.sort(list);
        return list;
    }

    @Override
    public List<DColumn> getColumns(String namespace, String storeName,
            String rowKey, Collection<String> columnNames) {
        ArrayList<DColumn> list = new ArrayList<>();
        String path = namespace + "/" + storeName + "/" + encode(rowKey) + "/";
        //List<String> keys = listAll(path);
        for(String columnName: columnNames) {
            byte[] value = get(path + encode(columnName));
            if(value == null) continue;
            list.add(new DColumn(columnName, value));
        }
        Collections.sort(list);
        return list;
    }

    @Override
    public List<String> getRows(String namespace, String storeName,
            String continuationToken, int count) {
        List<String> rows = new ArrayList<>();
        String path = namespace + "/" + storeName + "/";
        List<String> rowKeys = listAll(path);
        for(String row: rowKeys) {
            String rowName = decode(row);
            if(continuationToken != null && continuationToken.compareTo(rowName) >= 0) continue;
            rows.add(rowName);
            if(rows.size() >= count) break;
        }
        return rows;
    }


}