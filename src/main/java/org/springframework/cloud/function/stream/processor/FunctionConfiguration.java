/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.function.stream.processor;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.function.support.FluxFunction;
import org.springframework.cloud.function.support.FunctionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
@Configuration
@EnableConfigurationProperties(FunctionProperties.class)
public class FunctionConfiguration {

	@Autowired
	private ResourceLoader contextResourceLoader;

	@Autowired
	private FunctionProperties properties;

	@Bean
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Function<Flux<String>, Flux<String>> function() {
		URLClassLoader classLoader = null;
		try {
			URL url = null;
			String location = this.properties.getResource();
			if (location.startsWith("maven:")) {
				MavenResourceLoader mavenResourceLoader = new MavenResourceLoader(new MavenProperties());
				MavenResource resource = (MavenResource) mavenResourceLoader.getResource(location);
				url = new URL("file:" + resource.getFile().getAbsolutePath());
			}
			else {
				url = this.contextResourceLoader.getResource(this.properties.getResource()).getURL();
			}
			classLoader = new URLClassLoader(new URL[] { url }, this.getClass().getClassLoader());
			Class<?> clazz = classLoader.loadClass(this.properties.getClassname());
			Function<?, ?> function = (Function) clazz.newInstance();
			return (!FunctionUtils.isFluxFunction(function))
					? new FluxFunction<String, String>((Function<String, String>) function)
					: (Function<Flux<String>, Flux<String>>) function;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("failed to load function from resource", e);
		}
		finally {
			if (classLoader != null) {
				try {
					classLoader.close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
	}
}
