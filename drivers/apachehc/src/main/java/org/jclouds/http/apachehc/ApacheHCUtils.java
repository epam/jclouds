/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.http.apachehc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;
import jakarta.ws.rs.HttpMethod;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jclouds.JcloudsVersion;
import org.jclouds.http.HttpRequest;
import org.jclouds.io.ContentMetadataCodec;
import org.jclouds.io.MutableContentMetadata;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.BasePayload;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.io.payloads.DelegatingPayload;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.io.payloads.StringPayload;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;

@Singleton
public class ApacheHCUtils {
   //TODO: look up httpclient version
   public static final String USER_AGENT = String.format("jclouds/%s httpclient/%s", JcloudsVersion.get(), "5.4.3");
   
   private final ContentMetadataCodec contentMetadataCodec;

   public ApacheHCUtils(ContentMetadataCodec contentMetadataCodec) {
      this.contentMetadataCodec = contentMetadataCodec;
   }
   
   public HttpUriRequest convertToApacheRequest(HttpRequest request) {
      HttpUriRequestBase apacheRequest;
      if (request.getMethod().equals(HttpMethod.HEAD)) {
         apacheRequest = new HttpHead(request.getEndpoint());
      } else if (request.getMethod().equals(HttpMethod.GET)) {
         apacheRequest = new HttpGet(request.getEndpoint());
      } else if (request.getMethod().equals(HttpMethod.DELETE)) {
         apacheRequest = new HttpDelete(request.getEndpoint());
      } else if (request.getMethod().equals(HttpMethod.PUT)) {
         apacheRequest = new HttpPut(request.getEndpoint());
         apacheRequest.setConfig(RequestConfig.custom().setExpectContinueEnabled(true).build());
      } else if (request.getMethod().equals(HttpMethod.POST)) {
         apacheRequest = new HttpPost(request.getEndpoint());
      } else {
         apacheRequest = new HttpUriRequestBase(request.getMethod(), request.getEndpoint());
      }
      Payload payload = request.getPayload();

      // Since we may remove headers, ensure they are added to the apache
      // request after this block
      if (payload != null) {
         addEntityForContent(apacheRequest, payload);
      } else {
         apacheRequest.addHeader(HttpHeaders.CONTENT_LENGTH, "0");
      }

      for (Map.Entry<String, String> entry : request.getHeaders().entries()) {
         String header = entry.getKey();
         // apache automatically tries to add content length header
         if (!header.equals(HttpHeaders.CONTENT_LENGTH))
            apacheRequest.addHeader(header, entry.getValue());
      }
      apacheRequest.addHeader(HttpHeaders.USER_AGENT, USER_AGENT);
      return apacheRequest;
   }

   public void addEntityForContent(ClassicHttpRequest apacheRequest, Payload payload) {
      payload = payload instanceof DelegatingPayload ? DelegatingPayload.class.cast(payload).getDelegate() : payload;
      ContentType contentType = contentType(payload.getContentMetadata().getContentType());
      if (payload instanceof StringPayload) {
         apacheRequest.setEntity(new StringEntity((String) payload.getRawContent(), contentType));
      } else if (payload instanceof FilePayload) {
         apacheRequest.setEntity(new FileEntity((File) payload.getRawContent(), contentType));
      } else if (payload instanceof ByteArrayPayload) {
         apacheRequest.setEntity(new ByteArrayEntity((byte[]) payload.getRawContent(), contentType));
      } else {
         InputStream inputStream = payload.getInput();
         if (payload.getContentMetadata().getContentLength() == null)
            throw new IllegalArgumentException("you must specify size when content is an InputStream");
         apacheRequest.setEntity(new InputStreamEntity(inputStream, payload.getContentMetadata().getContentLength(),
               contentType));
      }

      // TODO Reproducing old behaviour exactly; ignoring Content-Type, Content-Length and Content-MD5
      Set<String> desiredHeaders = ImmutableSet.of("Content-Disposition", "Content-Encoding", "Content-Language", "Expires");
      MutableContentMetadata md = payload.getContentMetadata();
      for (Map.Entry<String, String> entry : contentMetadataCodec.toHeaders(md).entries()) {
         if (desiredHeaders.contains(entry.getKey())) {
            apacheRequest.addHeader(entry.getKey(), entry.getValue());
         }
      }
      
      assert apacheRequest.getEntity() != null;
   }

   private static ContentType contentType(String contentType) {
      return contentType != null ? ContentType.parse(contentType) : null;
   }

   public static class HttpEntityPayload extends BasePayload<HttpEntity> {

      HttpEntityPayload(HttpEntity content) {
         super(content);
         getContentMetadata().setContentType(content.getContentType());
         getContentMetadata().setContentLength(content.getContentLength());
      }

      @Override
      public InputStream openStream() throws IOException {
         try {
            return content.getContent();
         } catch (IllegalStateException e) {
            throw Throwables.propagate(e);
         }
      }

      @Override
      public boolean isRepeatable() {
         return content.isRepeatable();
      }

      @Override
      public void release() {
         try {
            EntityUtils.consume(content);
         } catch (IOException e) {
         }
      }
   }

}
