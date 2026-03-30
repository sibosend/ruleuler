/*******************************************************************************
 * Copyright 2017 Bstek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.bstek.urule;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bstek.urule.model.flow.FlowDefinition;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgePackageWrapper;
import com.bstek.urule.runtime.cache.CacheUtils;

/**
 * @author Jacky.gao
 * @since 2016年2月27日
 */
public class KnowledgePackageReceiverServlet extends HttpServlet {
	private static final long serialVersionUID = -4342175088856372588L;
	public static final String URL="/knowledgepackagereceiver";
	private static Logger log = LoggerFactory.getLogger(KnowledgePackageReceiverServlet.class);

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// logRequestDetails(req, "Received request:");
		String packageId=req.getParameter("packageId");
        SimpleDateFormat sd=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("["+sd.format(new Date())+"] "+"receive the server side request to push package:"+packageId+", content length:"+req.getContentLength());
		if(StringUtils.isEmpty(packageId)){
			return;
		}
		packageId=URLDecoder.decode(packageId, "utf-8");
		if(packageId.startsWith("/")){
			packageId=packageId.substring(1,packageId.length());
		}
		String content=req.getParameter("content");
		if(StringUtils.isEmpty(content)){
			return;
		}
		content=URLDecoder.decode(content, "utf-8");
		ObjectMapper mapper=new ObjectMapper();
		mapper.getDeserializationConfig().withDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
		KnowledgePackageWrapper wrapper=mapper.readValue(content, KnowledgePackageWrapper.class);
		wrapper.buildDeserialize();
		KnowledgePackage knowledgePackage=wrapper.getKnowledgePackage();
		Map<String, FlowDefinition> flowMap=knowledgePackage.getFlowMap();
		if(flowMap!=null && flowMap.size()>0){
			for(FlowDefinition fd:flowMap.values()){
				fd.buildConnectionToNode();
			}
		}
		CacheUtils.getKnowledgeCache().putKnowledge(packageId, knowledgePackage);

		System.out.println("["+sd.format(new Date())+"] "+"Successfully receive the server side to pushed package:"+packageId);
		resp.setContentType("text/plain");
		PrintWriter pw=resp.getWriter();
		pw.write("ok");
		pw.flush();
		pw.close();
	}

	private void logRequestDetails(HttpServletRequest req, String tag) {
		StringBuilder sb = new StringBuilder(tag)
			.append(" method=").append(req.getMethod())
			.append(" uri=").append(req.getRequestURI());
		if (req.getQueryString() != null) {
			sb.append('?').append(req.getQueryString());
		}

		Map<String, String[]> params = req.getParameterMap();
		if (!params.isEmpty()) {
			sb.append(" params=[");
			boolean first = true;
			for (Map.Entry<String, String[]> entry : params.entrySet()) {
				if (!first) {
					sb.append(", ");
				}
				sb.append(entry.getKey()).append("=")
				.append(Arrays.toString(entry.getValue()));
				first = false;
			}
			sb.append("]");
		}

		List<String> headerDump = new ArrayList<>();
		Enumeration<String> headerNames = req.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			headerDump.add(name + "=" + Collections.list(req.getHeaders(name)));
		}
		sb.append(" headers=").append(headerDump);

		log.info(sb.toString());
	}
}
