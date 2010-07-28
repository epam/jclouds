/**
 *
 * Copyright (C) 2009 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.chef;

import java.util.List;
import java.util.Properties;

import org.jclouds.chef.config.ChefRestClientModule;
import org.jclouds.ohai.config.ConfiguresOhai;
import org.jclouds.ohai.config.JMXOhaiJVMModule;
import org.jclouds.rest.RestContextBuilder;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * @author Adrian Cole
 */
public class ChefContextBuilder extends RestContextBuilder<ChefClient, ChefAsyncClient> {

   public ChefContextBuilder(Properties props) {
      super(ChefClient.class, ChefAsyncClient.class, props);
   }

   @Override
   protected void addClientModule(List<Module> modules) {
      modules.add(new ChefRestClientModule());
   }

   @Override
   public Injector buildInjector() {
      addOhaiModuleIfNotPresent();
      return super.buildInjector();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ChefContextBuilder withModules(Module... modules) {
      return (ChefContextBuilder) super.withModules(modules);
   }

   @SuppressWarnings("unchecked")
   @Override
   public ChefContext buildContext() {
      Injector injector = buildInjector();
      return injector.getInstance(ChefContext.class);
   }

   protected void addOhaiModuleIfNotPresent() {
      if (!Iterables.any(modules, new Predicate<Module>() {
         public boolean apply(Module input) {
            return input.getClass().isAnnotationPresent(ConfiguresOhai.class);
         }

      })) {
         addOhaiModule();
      }
   }

   protected void addOhaiModule() {
      modules.add(new JMXOhaiJVMModule());
   }
}