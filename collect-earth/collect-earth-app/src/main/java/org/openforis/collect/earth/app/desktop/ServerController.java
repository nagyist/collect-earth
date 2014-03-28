package org.openforis.collect.earth.app.desktop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.openforis.collect.earth.app.EarthConstants;
import org.openforis.collect.earth.app.EarthConstants.CollectDBDriver;
import org.openforis.collect.earth.app.service.LocalPropertiesService;
import org.openforis.collect.earth.app.service.LocalPropertiesService.EarthProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Controls the Jetty server, starting and stopping it as well as reporting its staus.
 * 
 * @author Alfonso Sanchez-Paus Diaz
 * 
 */
public class ServerController extends Observable {

	private static final String SAIKU_RDB_SUFFIX = "SaikuRDB";
	// Make sure that the default ports are the same for Server and Generator
	private static final String DEFAULT_PORT = "80";
	private Server server;
	private final Logger logger = LoggerFactory.getLogger(ServerController.class);
	private WebAppContext root;
	static LocalPropertiesService localPropertiesService = new LocalPropertiesService();

	public WebApplicationContext getContext() {
		return WebApplicationContextUtils.getRequiredWebApplicationContext(getRoot().getServletContext());
	}

	private static String getDbURL() {
		// jdbc:postgresql://hostname:port/dbname
		final CollectDBDriver collectDBDriver = localPropertiesService.getCollectDBDriver();

		String url = collectDBDriver.getUrl();
		url = url.replace("hostname", localPropertiesService.getValue(EarthProperty.DB_HOST));
		url = url.replace("port", localPropertiesService.getValue(EarthProperty.DB_PORT));
		url = url.replace("dbname", localPropertiesService.getValue(EarthProperty.DB_NAME));

		return url;
	}

	private int getPort() {

		try {
			localPropertiesService.init();
		} catch (final IOException e) {
			logger.error("Error initializing local properties", e);
		}

		String webPort = localPropertiesService.getLocalPort();
		if (webPort == null || webPort.isEmpty()) {
			webPort = DEFAULT_PORT;
		}
		return Integer.parseInt(webPort);
	}

	private WebAppContext getRoot() {
		return root;
	}

	public boolean isServerAlreadyRunning() {
		boolean alreadyRunning = false;
		try {
			new Socket("localhost", getPort()).close();
			// If here there is something is serving on port 8888
			// So stop it
			logger.warn("There is a server already running " + getPort());
			alreadyRunning = true;
		} catch (final IOException e) {
			// Nothing there, so OK to proceed
			logger.info("There is no server running in port " + getPort());
			alreadyRunning = false;
		}
		return alreadyRunning;
	}

	private void setRoot(WebAppContext root) {
		this.root = root;
	}

	private void initilizeDataSources() {
		final File jettyAppCtxTemplate = new File("resources/applicationContext.fmt");
		// Process the template file using the data in the "data" Map
		final Configuration cfg = new Configuration();

		try {
			cfg.setDirectoryForTemplateLoading(jettyAppCtxTemplate.getParentFile());

			// Load template from source folder
			final Template template = cfg.getTemplate(jettyAppCtxTemplate.getName());

			final File jettyAppCtx = new File(EarthConstants.GENERATED_FOLDER + "/applicationContext.xml");

			final Map<String, String> data = new java.util.HashMap<String, String>();

			data.put("driver", localPropertiesService.getCollectDBDriver().getDriverClass());
			data.put("url", getDbURL());
			data.put("urlSaiku", getSaikuDbURL());
			
			data.put("username", localPropertiesService.getValue(EarthProperty.DB_USERNAME));
			data.put("password", localPropertiesService.getValue(EarthProperty.DB_PASSWORD));

			// Console output
			BufferedWriter fw = null;
			try {
				fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jettyAppCtx), Charset.forName("UTF-8")));
				template.process(data, fw);
			} catch (final TemplateException e) {
				logger.error("Problemsa when processing the template for the Saiku data source", e);
			} finally {
				if (fw != null) {
					fw.close();
				}
			}
		} catch (final FileNotFoundException e) {
			logger.error("File not found", e);
		} catch (final IOException e) {
			logger.error("IO Exception", e);
		}
	}

	public static String getSaikuDbURL() {
		String urlSaikuDB = getDbURL();
		
		if( localPropertiesService.isUsingSqliteDB() ){
			urlSaikuDB += SAIKU_RDB_SUFFIX;
		}
		return urlSaikuDB;
	}

	/**
	 * @param highDemandServer
	 * @param args
	 */
	public void startServer(boolean highDemandServer, Observer observeInitialition) throws Exception {

		this.addObserver(observeInitialition);

		initilizeDataSources();

		final String webappDirLocation = "";

		// The port that we should run on can be set into an environment variable
		// Look for that variable and default to 8080 if it isn't there.
		// PropertyConfigurator.configure(this.getClass().getResource("/WEB-INF/conf/log4j.properties"));

		server = new Server();
		// // Use blocking-IO connector to improve throughput
		final Connector connector = new SocketConnector();
		connector.setPort(getPort());
		connector.setHost("0.0.0.0"); // Accept request from all IP addresses
		connector.setMaxIdleTime(600000);
		connector.setRequestBufferSize(10000);

		server.setConnectors(new Connector[] { connector });
		server.setThreadPool(new ExecutorThreadPool(10, 50, 5, TimeUnit.SECONDS));

		setRoot(new WebAppContext());

		getRoot().setContextPath("/earth");

		getRoot().setDescriptor(this.getClass().getResource("/WEB-INF/web.xml").toURI().toString());
		getRoot().setResourceBase(webappDirLocation);

		// Parent loader priority is a class loader setting that Jetty accepts.
		// By default Jetty will behave like most web containers in that it will
		// allow your application to replace non-server libraries that are part of the container.
		// Setting parent loader priority to true changes this behaviour.
		// Read more here:
		// http://wiki.eclipse.org/Jetty/Reference/Jetty_Classloading
		getRoot().setParentLoaderPriority(true);

		server.setHandler(getRoot());

		server.setGracefulShutdown(1000);
		server.setStopAtShutdown(true);
		server.start();

		setChanged();
		notifyObservers();

		server.join();
	}

	public void stopServer() throws Exception {
		if (server != null && server.isRunning()) {
			server.stop();
		}
	}

}
