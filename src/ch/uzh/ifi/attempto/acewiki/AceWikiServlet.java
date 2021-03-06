// This file is part of AceWiki.
// Copyright 2008-2013, AceWiki developers.
//
// AceWiki is free software: you can redistribute it and/or modify it under the terms of the GNU
// Lesser General Public License as published by the Free Software Foundation, either version 3 of
// the License, or (at your option) any later version.
//
// AceWiki is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
// even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License along with AceWiki. If
// not, see http://www.gnu.org/licenses/.

package ch.uzh.ifi.attempto.acewiki;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nextapp.echo.app.ApplicationInstance;
import nextapp.echo.webcontainer.WebContainerServlet;
import ch.uzh.ifi.attempto.base.APE;
import ch.uzh.ifi.attempto.base.LoggerContext;

/**
 * This servlet class is used by the web server to start AceWiki.
 * In order to run the AceWiki servlet, a web application archive (WAR) file has to be created.
 * See the <a href="{@docRoot}/README.txt">README file</a> and the
 * <a href="{@docRoot}/web.xml">web.xml example file</a>.
 *<p>
 * An APE should be accessible for the server, either directly installed on local or using socket
 * or web service. See the documentation of {@link APE} for more information.
 *<p>
 * For larger ontologies it might be necessary to adjust the stack and heap size, for example by
 * the following Java VM arguments:
 * <code>-Xmx400m -Xss4m</code>
 *
 * @author Tobias Kuhn
 * @author Yu Changyuan
 */
public class AceWikiServlet extends WebContainerServlet {

	private static final long serialVersionUID = -7342857942059126499L;

	private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(this.getClass());
	private LoggerContext loggerContext;
	private Backend backend;
	private Map<String, String> parameters;
	private String backendName;

	/**
	 * Creates a new AceWiki servlet object.
	 */
	public AceWikiServlet() {
	}

	/**
	 * Init the AceWiki servlet, get its Backend from ServletContext according
	 * to its config in web.xml or create backend if no 'backend' parameter
	 * exist.
	 *
	 * @param config servlet config.
	 */
	public void init(ServletConfig config) throws ServletException {
		parameters = getInitParameters(config);

		if (loggerContext == null) {
			loggerContext = new LoggerContext("syst", "syst", "0");
		}
		loggerContext.propagateWithinThread();
		org.slf4j.MDC.put("type", "appl");

		backendName = config.getInitParameter("backend");

		if (backendName != null) {
			log.info("use backend: {}", backendName);

			while (true) {
				backend = (Backend) config.getServletContext().getAttribute(backendName);

				if (backend != null) break;
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e) {
					break;
				}
			}

			// merge backend parameters
			Map<String, String> p = parameters;
			parameters = new HashMap<String,String>();
			parameters.putAll(backend.getParameters());
			parameters.putAll(p);
		} else {
			log.info("create backend");

			APE.setParameters(parameters);

			backend = new Backend(parameters);
		}

		super.init(config);
	}

	public ApplicationInstance newApplicationInstance() {
		loggerContext.propagateWithinThread();
		org.slf4j.MDC.put("type", "appl");
		log.info("new application instance: {}", parameters.get("ontology"));

		return new AceWikiApp(backend, parameters);
	}

	protected void process(HttpServletRequest request, HttpServletResponse response) throws
			IOException, ServletException {
		
		String params = "";
		String p;
		boolean hasInternalParams = false;

		// URLs of the form "...?showpage=ArticleName" can be used to access an article directly.
		// For the internal processing "...?page=ArticleName" is used.
		p = request.getParameter("showpage");
		if (p != null) params += "&page=" + p;
		if (request.getParameter("page") != null) hasInternalParams = true;

		// URLs of the form "...?showlang=Language" can be used to access a specific language
		// version of the wiki. For the internal processing "...?lang=Language" is used.
		p = request.getParameter("showlang");
		if (p != null) params += "&lang=" + p;
		if (request.getParameter("lang") != null) hasInternalParams = true;

		if (!request.getSession().isNew() && params.length() > 0) {
			response.sendRedirect(
					response.encodeRedirectURL("?sid=ExternalEvent" + params)
				);
		}
		if (params.length() == 0 && hasInternalParams && request.getParameter("sid") == null) {
			response.sendRedirect(response.encodeRedirectURL("."));
		}

		org.slf4j.MDC.put("type", "fail");
		try {
			super.process(request, response);
		} catch (RuntimeException | IOException | ServletException ex) {
			loggerContext.propagateWithinThread();
			log.error("fatal error", ex);
			ex.printStackTrace();
			throw ex;
		}
	}

	@SuppressWarnings("rawtypes")
	static Map<String, String> getInitParameters(ServletConfig config) {
		Map<String, String> initParameters = new HashMap<String, String>();
		Enumeration paramEnum = config.getInitParameterNames();
		while (paramEnum.hasMoreElements()) {
			String n = paramEnum.nextElement().toString();
			initParameters.put(n, config.getInitParameter(n));
		}
		Enumeration contextParamEnum = config.getServletContext().getInitParameterNames();
		while (contextParamEnum.hasMoreElements()) {
			String n = contextParamEnum.nextElement().toString();
			initParameters.put("context:" + n, config.getServletContext().getInitParameter(n));
		}

		// Set default parameters:
		if (initParameters.get("context:apecommand") == null) {
			initParameters.put("context:apecommand", "ape.exe");
		}

		if (initParameters.get("context:logdir") == null) {
			initParameters.put("context:logdir", "logs");
		}

		if (initParameters.get("context:datadir") == null) {
			initParameters.put("context:datadir", "data");
		}
		
		return initParameters;
	}

}
