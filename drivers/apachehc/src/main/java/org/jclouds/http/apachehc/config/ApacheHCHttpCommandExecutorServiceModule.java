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
package org.jclouds.http.apachehc.config;

import java.io.Closeable;
import java.io.IOException;
import java.net.ProxySelector;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.util.Timeout;
import org.jclouds.http.HttpCommandExecutorService;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.apachehc.ApacheHCHttpCommandExecutorService;
import org.jclouds.http.config.ConfiguresHttpCommandExecutorService;
import org.jclouds.http.config.SSLModule;
import org.jclouds.lifecycle.Closer;
import org.jclouds.proxy.ProxyConfig;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

/**
 * Configures {@link ApacheHCHttpCommandExecutorService}.
 * 
 * Note that this uses threads
 */
@ConfiguresHttpCommandExecutorService
public class ApacheHCHttpCommandExecutorServiceModule extends AbstractModule {

   @Override
   protected void configure() {
      install(new SSLModule());
      bindClient();
   }

   @Singleton
   @Provides
   final HostnameVerifier newHostnameVerifier(HttpUtils utils) {
      return utils.relaxHostname() ? NoopHostnameVerifier.INSTANCE : new DefaultHostnameVerifier();
   }

   @Singleton
   @Provides
   final SSLContext newSSLContext(HttpUtils utils, @Named("untrusted") Supplier<SSLContext> untrustedSSLContextProvider)
            throws NoSuchAlgorithmException, KeyManagementException {
      if (utils.trustAllCerts())
         return untrustedSSLContextProvider.get();
      SSLContext context = SSLContext.getInstance("TLS");

      context.init(null, null, null);
      return context;
   }

   @Singleton
   @Provides
   final PoolingHttpClientConnectionManager newClientConnectionManager(HttpUtils utils, HostnameVerifier verifier,
            SSLContext context, Closer closer) {

      SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
            .setSslContext(context).setHostnameVerifier(verifier).build();

      ConnectionConfig.Builder connectionConfig = ConnectionConfig.custom();
      if (utils.getConnectionTimeout() > 0) {
         connectionConfig.setConnectTimeout(Timeout.ofMilliseconds(utils.getConnectionTimeout()));
      }
      if (utils.getSocketOpenTimeout() > 0) {
         connectionConfig.setSocketTimeout(Timeout.ofMilliseconds(utils.getSocketOpenTimeout()));
      }

      PoolingHttpClientConnectionManagerBuilder builder = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory).setDefaultConnectionConfig(connectionConfig.build());

      if (utils.getMaxConnections() > 0) {
         builder.setMaxConnTotal(utils.getMaxConnections());
      }
      if (utils.getMaxConnectionsPerHost() > 0) {
         builder.setMaxConnPerRoute(utils.getMaxConnectionsPerHost());
      }

      final PoolingHttpClientConnectionManager cm = builder.build();
      closer.addToClose(new Closeable() {
         @Override
         public void close() throws IOException {
            cm.close();
         }
      });
      return cm;
   }

   @Provides
   @Singleton
   final HttpClient newDefaultHttpClient(ProxyConfig config, PoolingHttpClientConnectionManager cm) {
      HttpClientBuilder builder = HttpClients.custom().setConnectionManager(cm);
      if (config.useSystem()) {
         builder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
      }
      return builder.build();
   }

   protected void bindClient() {
      bind(HttpCommandExecutorService.class).to(ApacheHCHttpCommandExecutorService.class).in(Scopes.SINGLETON);
   }

}
