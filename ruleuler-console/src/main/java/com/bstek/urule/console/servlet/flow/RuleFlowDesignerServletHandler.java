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
package com.bstek.urule.console.servlet.flow;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.bstek.urule.RuleException;
import com.bstek.urule.Utils;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.bstek.urule.console.servlet.RenderPageServletHandler;
import com.bstek.urule.model.flow.FlowDefinition;
import com.bstek.urule.parse.deserializer.FlowDeserializer;

/**
 * @author Jacky.gao
 * @since 2016年6月3日
 */
public class RuleFlowDesignerServletHandler extends RenderPageServletHandler {
	private RepositoryService repositoryService;
	private FlowDeserializer flowDeserializer;
	@Override
	public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method=retriveMethod(req);
		if(method!=null){
			invokeMethod(method, req, resp);
		}else{
			VelocityContext context = new VelocityContext();
			context.put("contextPath", req.getContextPath());
			String file=req.getParameter("file");
			String project = buildProjectNameFromFile(file);
			if(project!=null){
				context.put("project", project);
			}
			resp.setContentType("text/html");
			resp.setCharacterEncoding("utf-8");
			Template template=ve.getTemplate("html/rule-flow-designer.html","utf-8");
			PrintWriter writer=resp.getWriter();
			template.merge(context, writer);
			writer.close();
		}
	}

	public void loadFlowDefinition(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream inputStream;
		String file=req.getParameter("file");
		String version=req.getParameter("version");
		file=Utils.decodeURL(file);
		try{
			if(StringUtils.isEmpty(version)){
				inputStream=repositoryService.readFile(file,null);
			}else{
				inputStream=repositoryService.readFile(file,version);
			}
			Element root=parseXml(inputStream);
			FlowDefinition fd = flowDeserializer.deserialize(root);
			inputStream.close();
			writeObjectToJson(resp, new FlowDefinitionWrapper(fd));
		}catch(Exception ex){
			throw new RuleException(ex);
		}
	}
	
	protected Element parseXml(InputStream stream){
		SAXReader reader=new SAXReader();
		Document document;
		try {
			document = reader.read(stream);
			Element root=document.getRootElement();
			return root;
		} catch (DocumentException e) {
			throw new RuleException(e);
		}
	}
	
	public void loadPackages(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String project=req.getParameter("project");
		try{
			List<ResourcePackage> packages=repositoryService.loadProjectResourcePackages(project);		
			writeObjectToJson(resp, packages);
		}catch(Exception ex){
			throw new RuleException(ex);
		}
	}
	
	public void setRepositoryService(RepositoryService repositoryService) {
		this.repositoryService = repositoryService;
	}

	/**
	 * 根据一组规则文件路径，解析它们依赖的库文件。
	 * 请求参数 files: 逗号分隔的文件路径列表（不含前缀）
	 * 返回: { "variable": [...], "constant": [...], "parameter": [...], "action": [...] }
	 */
	public void resolveLibraries(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String filesParam = req.getParameter("files");
		if (StringUtils.isBlank(filesParam)) {
			writeObjectToJson(resp, Map.of("variable", List.of(), "constant", List.of(), "parameter", List.of(), "action", List.of()));
			return;
		}
		Set<String> variableLibs = new LinkedHashSet<>();
		Set<String> constantLibs = new LinkedHashSet<>();
		Set<String> parameterLibs = new LinkedHashSet<>();
		Set<String> actionLibs = new LinkedHashSet<>();

		String[] files = filesParam.split(",");
		for (String file : files) {
			file = file.trim();
			if (file.isEmpty()) continue;
			file = Utils.decodeURL(file);
			try (InputStream is = repositoryService.readFile(file, null)) {
				if (is == null) continue;
				Element root = parseXml(is);
				for (Object obj : root.elements()) {
					Element ele = (Element) obj;
					String name = ele.getName();
					String path = ele.attributeValue("path");
					if (path == null) continue;
					switch (name) {
						case "import-variable-library": variableLibs.add(path); break;
						case "import-constant-library": constantLibs.add(path); break;
						case "import-parameter-library": parameterLibs.add(path); break;
						case "import-action-library": actionLibs.add(path); break;
					}
				}
			} catch (Exception ex) {
				// 跳过无法读取的文件
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("variable", new ArrayList<>(variableLibs));
		result.put("constant", new ArrayList<>(constantLibs));
		result.put("parameter", new ArrayList<>(parameterLibs));
		result.put("action", new ArrayList<>(actionLibs));
		writeObjectToJson(resp, result);
	}

	public void setFlowDeserializer(FlowDeserializer flowDeserializer) {
		this.flowDeserializer = flowDeserializer;
	}
	
	@Override
	public String url() {
		return "/ruleflowdesigner";
	}
}
