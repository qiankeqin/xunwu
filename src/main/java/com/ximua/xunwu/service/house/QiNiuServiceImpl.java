package com.ximua.xunwu.service.house;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;

@Service
public class QiNiuServiceImpl implements IQiNiuService,InitializingBean {
    @Autowired
    private UploadManager uploadManager;

    @Autowired
    private BucketManager bucketManager;

    @Autowired
    private Auth auth;

    @Value("${qiniu.Bucket}")
    private String bucket;


    private StringMap putPolicy;

    @Override
    public Response uploadFile(File file) throws QiniuException {
        Response response = this.uploadManager.put(file, null, getUploadToken());
        int retryTimes = 0;
        while(response.needRetry() && retryTimes<3){//是否需要重，并且重传次数少于3次
            response = this.uploadManager.put(file,null,getUploadToken());
            retryTimes++;
        }
        return response;
    }

    /**
     * 七牛云上传文件功能
     * @param inputStream
     * @return
     * @throws QiniuException
     */
    @Override
    public Response uploadFile(InputStream inputStream) throws QiniuException {
        Response response = this.uploadManager.put(inputStream, null, getUploadToken(),null,null);
        int retryTimes = 0;
        while(response.needRetry() && retryTimes<3){//是否需要重传
            response = this.uploadManager.put(inputStream,null,getUploadToken(),null,null);
            retryTimes++;
        }
        return response;
    }

    /**
     * 七牛云删除文件功能
     * @param key 文件唯一ke，上传成功后，会返回一个key，如FhRdUkZQvlByVYno45zVT6DbDS60
     * @return
     * @throws QiniuException
     */
    @Override
    public Response delete(String key) throws QiniuException {
        Response response = bucketManager.delete(this.bucket, key);
        int retryTimes = 0;
        while(response.needRetry() && retryTimes<3){
            response = bucketManager.delete(this.bucket, key);
            retryTimes++;
        }
        return response;
    }

    /**
     * 在properties注入后，进行初始化
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        this.putPolicy = new StringMap();
        //key，value，空间名，宽度，高度
        putPolicy.put("returnBody", "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"bucket\":\"$(bucket)\",\"width\":$(imageInfo.width),\"height\":$(imageInfo.height)}");
    }

    /**
     * 获取上传凭证
     * @return
     */
    private String getUploadToken(){
        return this.auth.uploadToken(bucket,null,3600,putPolicy);
    }
}
