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

package com.chattopadhyaya;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.solr.api.Command;
import org.apache.solr.api.EndPoint;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.ReplicationHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.PermissionNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@EndPoint(path = "/plugin/solrui/*",
method = METHOD.GET,
permission = PermissionNameProvider.Name.CONFIG_READ_PERM)
public class UIHandler implements ResourceLoaderAware {

	final public static String PLUGIN_PATH = "/plugin/solrui";

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final CoreContainer coreContainer;

	private ResourceLoader loader = null;

	public UIHandler(CoreContainer coreContainer) {
		this.coreContainer = coreContainer;
	}


	@Override
	public void inform(ResourceLoader loader) throws IOException {
		this.loader = loader;		
	}

	@Command
	public void call(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException{
		String path = req.getHttpSolrCall().getPath(); // Can be like this: /__v2/plugin/uihandler/index.htm
		String filepath = path.substring(path.indexOf(PLUGIN_PATH) + PLUGIN_PATH.length());

		if ("".equalsIgnoreCase(filepath) || "/".equalsIgnoreCase(filepath)) {
			filepath = "index.html";
		}
		if (filepath.startsWith("/")) filepath = filepath.substring(1);

		// HACK: If this is not a request for static content, but a request to a Solr endpoint, try to forward to the
		// right place. A proper UI impl should never make Solr API calls to this endpoint.
		// This is a security vulnerability, NEVER USE THE FOLLOWING IN PRODUCTION.
		if (forwardIfNeeded(req, rsp, filepath)) return;

		ModifiableSolrParams newparams = new ModifiableSolrParams(req.getOriginalParams());
		newparams.set(CommonParams.WT, ReplicationHandler.FILE_STREAM);
		req.setParams(newparams);

		InputStream in = loader.openResource(filepath);
		log.info("Trying to access: "+filepath);
		byte data[] = IOUtils.toByteArray(in);

		String contentType = getContentType(filepath);

		SolrCore.RawWriter writer = new SolrCore.RawWriter() {

			@Override
			public void write(OutputStream os) throws IOException {
				os.write(data);
			}

			@Override
			public String getContentType() {
				return contentType;
			}
		};

		rsp.add(ReplicationHandler.FILE_STREAM, writer);
	}

	String getContentType(String filepath) {
		Map<String, String> types = Map.of(
				"jpg", "image/jpg",
				"png", "image/png",
				"gif", "image/gif",
				"svg", "image/svg+xml",
				"htm", "text/html",
				"html", "text/html",
				"js", "text/javascript",
				"css", "text/css",
				"json", "application/json",
				"xml", "application/xml"
				);
		String extension = filepath.split("\\.")[filepath.split("\\.").length-1];
		if (types.containsKey(extension)) {
			return types.get(extension);
		} else {
			return "text/plain";
		}
	}

	private boolean forwardIfNeeded(SolrQueryRequest req, SolrQueryResponse rsp, String filepath) {
		String whitelist[] = {"css", "favicon.ico", "img", "index.html", "js", "libs", "manifest.json", "partials", "webapp", "WEB-INF"};
		boolean needsForwarding = true;
		for (String w: whitelist) {
			if (filepath.startsWith("/"+w)) {
				needsForwarding = false;
			}
		}
		if (needsForwarding) {
			forward(req, filepath, req.getParams(), rsp);
			return true;
		}
		return false;
	}

	private void forward(SolrQueryRequest req, String path, SolrParams params, SolrQueryResponse rsp){
		LocalSolrQueryRequest r = new LocalSolrQueryRequest(req.getCore(), params);
		SolrRequestHandler rh = coreContainer.getRequestHandler(path);
		if (rh == null) {
			path = path.startsWith("/")? path.substring(1): path;
			String first = path.split("/")[0];
			if (coreContainer.getCore(first) != null) {
				String handlerPath = path.substring(first.length());
				rh = coreContainer.getCore(first).getRequestHandler(handlerPath);
			}
		}
		rh.handleRequest(r, rsp);
	}

}
