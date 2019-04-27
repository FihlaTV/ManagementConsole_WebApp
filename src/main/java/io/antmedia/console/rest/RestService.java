package io.antmedia.console.rest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.qos.logback.classic.Level;
import io.antmedia.AppSettingsModel;
import io.antmedia.SystemUtils;
import io.antmedia.console.AdminApplication;
import io.antmedia.console.AdminApplication.ApplicationInfo;
import io.antmedia.console.AdminApplication.BroadcastInfo;
import io.antmedia.console.datastore.DataStoreFactory;
import io.antmedia.console.datastore.IDataStore;
import io.antmedia.datastore.AppSettingsManager;
import io.antmedia.datastore.db.types.Licence;
import io.antmedia.datastore.preference.PreferenceStore;
import io.antmedia.licence.ILicenceService;
import io.antmedia.rest.BroadcastRestService;
import io.antmedia.rest.model.Result;
import io.antmedia.rest.model.User;
import io.antmedia.rest.model.UserType;
import io.antmedia.settings.LogSettings;
import io.antmedia.settings.ServerSettings;
import io.antmedia.statistic.GPUUtils;
import io.antmedia.statistic.GPUUtils.MemoryStatus;
import io.antmedia.statistic.HlsViewerStats;
import io.antmedia.statistic.ResourceMonitor;
import io.antmedia.webrtc.api.IWebRTCAdaptor;

@Component
@Path("/")
public class RestService {
		
	private static final String LOG_LEVEL_ALL = "ALL";

	private static final String LOG_LEVEL_TRACE = "TRACE";

	private static final String LOG_LEVEL_DEBUG = "DEBUG";

	private static final String LOG_LEVEL_INFO = "INFO";

	private static final String LOG_LEVEL_WARN = "WARN";

	private static final String LOG_LEVEL_ERROR = "ERROR";

	private static final String LOG_LEVEL_OFF = "OFF";

	private static final String USER_PASSWORD = "user.password";

	private static final String USER_EMAIL = "user.email";

	public static final String IS_AUTHENTICATED = "isAuthenticated";

	public static final String SERVER_NAME = "server.name";

	public static final String LICENSE_KEY = "server.licence_key";

	public static final String MARKET_BUILD = "server.market_build";

	Gson gson = new Gson();

	private IDataStore dataStore;

	private static final String LOG_LEVEL = "logLevel";

	private static final String RED5_PROPERTIES = "red5.properties";

	private static final String RED5_PROPERTIES_PATH = "conf/red5.properties";

	protected static final Logger logger = LoggerFactory.getLogger(RestService.class);

	private static final String SOFTWARE_VERSION = "softwareVersion";

	protected ApplicationContext applicationContext;

	@Context 
	private ServletContext servletContext;

	@Context
	private HttpServletRequest servletRequest;

	private DataStoreFactory dataStoreFactory;
	private ServerSettings serverSettings;

	private ILicenceService licenceService;




	/**
	 * Add user account on db. 
	 * Username must be unique,
	 * if there is a user with the same name, user will not be created
	 * 
	 * userType = 0 means ready only account
	 * userType = 1 means read-write account
	 * 
	 * Post method should be used.
	 * 
	 * application/json
	 * 
	 * form parameters - case sensitive
	 * "userName", "password", "userType
	 * 
	 * @param userName
	 * @return JSON data
	 * if user is added success will be true
	 * if user is not added success will be false
	 * 	if user is not added, errorId = 1 means username already exist
	 */
	@POST
	@Path("/addUser")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result addUser(User user) {
		//TODO: check that request is coming from authorized user
		boolean result = false;
		int errorId = -1;
		if (user != null && !getDataStore().doesUsernameExist(user.getEmail())) {
			result = getDataStore().addUser(user.getEmail(), user.getPassword(), UserType.ADMIN);
		}
		else {
			if (user == null) {
				logger.info("user variable null");
			}
			else {
				logger.info("user already exist in db");
			}

			errorId = 1;
		}
		Result operationResult = new Result(result);
		operationResult.setErrorId(errorId);
		return operationResult;
	}


	@POST
	@Path("/addInitialUser")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result addInitialUser(User user) {
		boolean result = false;
		int errorId = -1;
		if (getDataStore().getNumberOfUserRecords() == 0) {
			result = getDataStore().addUser(user.getEmail(), user.getPassword(), UserType.ADMIN);
		}

		Result operationResult = new Result(result);
		operationResult.setErrorId(errorId);
		return operationResult;
	}

	@GET
	@Path("/isFirstLogin")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result isFirstLogin() 
	{
		boolean result = false;
		if (getDataStore().getNumberOfUserRecords() == 0) {
			result = true;
		}
		return new Result(result);
	}

	/**
	 * Edit user account on db. 
	 * Username cannot be changed, password or userType can be changed
	 * userType = 0 means ready only account
	 * userType = 1 means read-write account
	 * 
	 * Post method should be used.
	 * 
	 * application/x-www-form-urlencoded
	 * 
	 * form parameters - case sensitive
	 * "userName", "password", "userType
	 * 
	 * @param userName
	 * @return JSON data
	 * if user is edited, success will be true
	 * if not, success will be false
	 * 	errorId = 2 means user does not exist
	 */
	/*
	@POST
	@Path("/editUser")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public OperationResult editUser(@FormParam("userName") String userName, @FormParam("password") String password, @FormParam("userType") Integer userType) {
		//TODO: check that request is coming from authorized user
		boolean result = false;
		int errorId = -1;
		if (userName != null && getDataStore().doesUsernameExist(userName)) {
			result = getDataStore().editUser(userName, password, userType);
		}
		else {
			errorId = 2;
		}

		OperationResult operationResult = new OperationResult(result);
		operationResult.setErrorId(errorId);
		return operationResult;
	}
	 */

	/**
	 * Deletes user account from db
	 * 
	 * Post method should be used.
	 * 
	 * application/x-www-form-urlencoded
	 * 
	 * form parameters - case sensitive
	 * "userName"
	 * 
	 * @param userName
	 * @return
	 */
	/*
	@POST
	@Path("/deleteUser")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public OperationResult deleteUser(@FormParam("userName") String userName) {
		//TODO: check that request is coming from authorized user
		boolean result = getDataStore().deleteUser(userName);
		return new OperationResult(result);
	}
	 */



	/**
	 * Authenticates user with userName and password
	 * 
	 * 
	 * @param user
	 * @return json that shows user is authenticated or not
	 */
	@POST
	@Path("/authenticateUser")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result authenticateUser(User user) {
		boolean result = getDataStore().doesUserExist(user.getEmail(), user.getPassword());
		if (result) {
			HttpSession session = servletRequest.getSession();
			session.setAttribute(IS_AUTHENTICATED, true);
			session.setAttribute(USER_EMAIL, user.getEmail());
			session.setAttribute(USER_PASSWORD, user.getPassword());
		}
		return new Result(result);
	}


	@POST
	@Path("/changeUserPassword")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result changeUserPassword(User user) {

		String userMail = (String)servletRequest.getSession().getAttribute(USER_EMAIL);

		return changeUserPasswordInternal(userMail, user);

	}

	public Result changeUserPasswordInternal(String userMail, User user) {
		boolean result = false;
		String message = null;
		if (userMail != null) {
			result = getDataStore().doesUserExist(userMail, user.getPassword());
			if (result) {
				result = getDataStore().editUser(userMail, user.getNewPassword(), UserType.ADMIN);

				if (result) {
					HttpSession session = servletRequest.getSession();
					if (session != null) {
						session.setAttribute(IS_AUTHENTICATED, true);
						session.setAttribute(USER_EMAIL, userMail);
						session.setAttribute(USER_PASSWORD, user.getNewPassword());
					}
				}
			}
			else {
				message = "User not exist with that name and pass";
			}
		}
		else {
			message = "User name does not exist in context";
		}

		return new Result(result, message);
	}




	@GET
	@Path("/isAuthenticated")
	@Produces(MediaType.APPLICATION_JSON)
	public Result isAuthenticatedRest(){
		return new Result(isAuthenticated(servletRequest.getSession()));
	}

	public static boolean isAuthenticated(HttpSession session) 
	{

		Object isAuthenticated = session.getAttribute(IS_AUTHENTICATED);
		Object userEmail = session.getAttribute(USER_EMAIL);
		Object userPassword = session.getAttribute(USER_PASSWORD);
		boolean result = false;
		if (isAuthenticated != null && userEmail != null && userPassword != null) {
			result = true;
		}
		return result;
	}

	/*
	 * 	os.name						:Operating System Name
	 * 	os.arch						: x86/x64/...
	 * 	java.specification.version	: Java Version (Required 1.5 or 1.6 and higher to run Red5)
	 * 	-------------------------------
	 * 	Runtime.getRuntime()._____  (Java Virtual Machine Memory)
	 * 	===============================
	 * 	maxMemory()					: Maximum limitation
	 * 	totalMemory()				: Total can be used
	 * 	freeMemory()				: Availability
	 * 	totalMemory()-freeMemory()	: In Use
	 * 	availableProcessors()		: Total Processors available
	 * 	-------------------------------
	 *  getOperatingSystemMXBean()	(Actual Operating System RAM)
	 *	===============================
	 *  osCommittedVirtualMemory()	: Virtual Memory
	 *  osTotalPhysicalMemory()		: Total Physical Memory
	 *  osFreePhysicalMemory()		: Available Physical Memory
	 *  osInUsePhysicalMemory()		: In Use Physical Memory
	 *  osTotalSwapSpace()			: Total Swap Space
	 *  osFreeSwapSpace()			: Available Swap Space
	 *  osInUseSwapSpace()			: In Use Swap Space
	 *  -------------------------------
	 *  File						(Actual Harddrive Info: Supported for JRE 1.6)
	 *	===============================
	 *	osHDUsableSpace()			: Usable Space
	 *	osHDTotalSpace()			: Total Space
	 *	osHDFreeSpace()				: Available Space
	 *	osHDInUseSpace()			: In Use Space
	 **/


	@GET
	@Path("/getSystemInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getSystemInfo() {
		return gson.toJson(ResourceMonitor.getSystemInfoJSObject());
	}


	/*
	 * 	Runtime.getRuntime()._____  (Java Virtual Machine Memory)
	 * 	===============================
	 * 	maxMemory()					: Maximum limitation
	 * 	totalMemory()				: Total can be used
	 * 	freeMemory()				: Availability
	 * 	totalMemory()-freeMemory()	: In Use
	 * 	availableProcessors()		: Total Processors available
	 */
	@GET
	@Path("/getJVMMemoryInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getJVMMemoryInfo() {
		return gson.toJson(ResourceMonitor.getJVMMemoryInfoJSObject());
	}


	/*
	 *  osCommittedVirtualMemory()	: Virtual Memory
	 *  osTotalPhysicalMemory()		: Total Physical Memory
	 *  osFreePhysicalMemory()		: Available Physical Memory
	 *  osInUsePhysicalMemory()		: In Use Physical Memory
	 *  osTotalSwapSpace()			: Total Swap Space
	 *  osFreeSwapSpace()			: Available Swap Space
	 *  osInUseSwapSpace()			: In Use Swap Space
	 */
	@GET
	@Path("/getSystemMemoryInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getSystemMemoryInfo() {
		return gson.toJson(ResourceMonitor.getSysteMemoryInfoJSObject());
	}


	/*
	 *  File						(Actual Harddrive Info: Supported for JRE 1.6)
	 *	===============================
	 *	osHDUsableSpace()			: Usable Space
	 *	osHDTotalSpace()			: Total Space
	 *	osHDFreeSpace()				: Available Space
	 *	osHDInUseSpace()			: In Use Space
	 **/
	@GET
	@Path("/getFileSystemInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getFileSystemInfo() {
		return gson.toJson(ResourceMonitor.getFileSystemInfoJSObject());
	}

	/**
	 * getProcessCpuTime:  microseconds CPU time used by the process
	 * 
	 * getSystemCpuLoad:	"% recent cpu usage" for the whole system. 
	 * 
	 * getProcessCpuLoad: "% recent cpu usage" for the Java Virtual Machine process. 
	 * @return
	 */
	@GET
	@Path("/getCPUInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getCPUInfo() {
		return gson.toJson(ResourceMonitor.getCPUInfoJSObject());
	}



	@GET
	@Path("/getSystemResourcesInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getSystemResourcesInfo() {
		
		AdminApplication application = getApplication();
		JsonObject jsonObject = ResourceMonitor.getSystemResourcesInfo(application.getRootScope());
		


		//add live stream size
		int totalLiveStreams = 0;
		
		List<String> appNames = application.getApplications();
		for (String name : appNames) 
		{
			IScope scope = application.getRootScope().getScope(name);
			totalLiveStreams += application.getAppLiveStreamCount(scope);
		}

		jsonObject.addProperty(ResourceMonitor.TOTAL_LIVE_STREAMS, totalLiveStreams);

		return gson.toJson(jsonObject);
	}

	@GET
	@Path("/getGPUInfo")
	@Produces(MediaType.APPLICATION_JSON) 
	public String getGPUInfo() 
	{
		return gson.toJson(ResourceMonitor.getGPUInfoJSObject());
	}


	@GET
	@Path("/getVersion")
	@Produces(MediaType.APPLICATION_JSON) 
	public String getVersion() {
		return gson.toJson(BroadcastRestService.getSoftwareVersion());
	}


	@GET
	@Path("/getApplications")
	@Produces(MediaType.APPLICATION_JSON)
	public String getApplications() {
		List<String> applications = getApplication().getApplications();
		JsonObject jsonObject = new JsonObject();
		JsonArray jsonArray = new JsonArray();

		for (String appName : applications) {
			if (!appName.equals(AdminApplication.APP_NAME)) {
				jsonArray.add(appName);
			}
		}
		jsonObject.add("applications", jsonArray);
		return gson.toJson(jsonObject);
	}

	/**
	 * Refactor name getTotalLiveStreamSize
	 * only return totalLiveStreamSize
	 * @return
	 */
	@GET
	@Path("/getLiveClientsSize")
	@Produces(MediaType.APPLICATION_JSON)
	public String getLiveClientsSize() 
	{
		int totalConnectionSize = getApplication().getTotalConnectionSize();
		int totalLiveStreamSize = getApplication().getTotalLiveStreamSize();
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("totalConnectionSize", totalConnectionSize);
		jsonObject.addProperty(ResourceMonitor.TOTAL_LIVE_STREAMS, totalLiveStreamSize);

		return gson.toJson(jsonObject);
	}

	@GET
	@Path("/getApplicationsInfo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getApplicationInfo() {
		List<ApplicationInfo> info = getApplication().getApplicationInfo();
		return gson.toJson(info);
	}

	/**
	 * Refactor remove this function and use ProxyServlet to get this info
	 * Before deleting check web panel does not use it
	 * @param name
	 * @return
	 */
	@GET
	@Path("/getAppLiveStreams/{appname}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getAppLiveStreams(@PathParam("appname") String name) {
		List<BroadcastInfo> appLiveStreams = getApplication().getAppLiveStreams(name);
		return gson.toJson(appLiveStreams);
	}


	/**
	 * Refactor remove this function and use ProxyServlet to get this info
	 * Before deleting check web panel does not use it
	 * @param name
	 * @return
	 */
	@POST
	@Path("/deleteVoDStream/{appname}")
	@Produces(MediaType.APPLICATION_JSON)
	public String deleteVoDStream(@PathParam("appname") String name, @FormParam("streamName") String streamName) {
		boolean deleteVoDStream = getApplication().deleteVoDStream(name, streamName);
		return gson.toJson(new Result(deleteVoDStream));
	}


	@POST
	@Path("/changeSettings/{appname}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String changeSettings(@PathParam("appname") String appname, AppSettingsModel appsettings){

		ApplicationContext context = getApplication().getApplicationContext(appname);
		return gson.toJson(new Result(AppSettingsManager.updateAppSettings(context, appsettings, true)));


	}



	@POST
	@Path("/changeServerSettings")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String changeServerSettings(ServerSettings serverSettings){


		PreferenceStore store = new PreferenceStore(RED5_PROPERTIES_PATH, true);

		String serverName = "";
		String licenceKey = "";
		if(serverSettings.getServerName() != null) {
			serverName = serverSettings.getServerName();
		}

		store.put(SERVER_NAME, serverName);
		getServerSettingsInternal().setServerName(serverName);

		if (serverSettings.getLicenceKey() != null) {
			licenceKey = serverSettings.getLicenceKey();
		}

		store.put(LICENSE_KEY, licenceKey);
		getServerSettingsInternal().setLicenceKey(licenceKey);

		store.put(MARKET_BUILD, String.valueOf(serverSettings.isBuildForMarket()));
		getServerSettingsInternal().setBuildForMarket(serverSettings.isBuildForMarket());


		return gson.toJson(new Result(store.save()));
	}

	@GET
	@Path("/isEnterpriseEdition")
	@Produces(MediaType.APPLICATION_JSON)
	public Result isEnterpriseEdition(){
		boolean isEnterprise = BroadcastRestService.isEnterprise();
		return new Result(isEnterprise, "");
	}

	@GET
	@Path("/getSettings/{appname}")
	@Produces(MediaType.APPLICATION_JSON)
	public AppSettingsModel getSettings(@PathParam("appname") String appname) 
	{

		return AppSettingsManager.getAppSettings(appname);

	}

	@GET
	@Path("/getServerSettings")
	@Produces(MediaType.APPLICATION_JSON)
	public ServerSettings getServerSettings() 
	{

		return getServerSettingsInternal();
	}



	@GET
	@Path("/getLicenceStatus")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Licence getLicenceStatus(@QueryParam("key") String key) 
	{
		if(key == null) {
			return null;
		}
		return getLicenceServiceInstance().checkLicence(key);
	}


	public void setDataStore(IDataStore dataStore) {
		this.dataStore = dataStore;
	}

	public IDataStore getDataStore() {
		if (dataStore == null) {
			dataStore = getDataStoreFactory().getDataStore();
		}
		return dataStore;
	}

	private ServerSettings getServerSettingsInternal() {

		if(serverSettings == null) {

			WebApplicationContext ctxt = WebApplicationContextUtils.getWebApplicationContext(servletContext); 
			serverSettings = (ServerSettings)ctxt.getBean(ServerSettings.BEAN_NAME);
		}
		return serverSettings;
	}



	public ILicenceService getLicenceServiceInstance () {
		if(licenceService == null) {

			WebApplicationContext ctxt = WebApplicationContextUtils.getWebApplicationContext(servletContext); 
			licenceService = (ILicenceService)ctxt.getBean(ILicenceService.BeanName.LICENCE_SERVICE.toString());
		}
		return licenceService;
	}


	public AdminApplication getApplication() {
		WebApplicationContext ctxt = WebApplicationContextUtils.getWebApplicationContext(servletContext); 
		return (AdminApplication)ctxt.getBean("web.handler");
	}

	public DataStoreFactory getDataStoreFactory() {
		if(dataStoreFactory == null)
		{
			WebApplicationContext ctxt = WebApplicationContextUtils.getWebApplicationContext(servletContext); 
			dataStoreFactory = (DataStoreFactory) ctxt.getBean("dataStoreFactory");
		}
		return dataStoreFactory;
	}

	public void setDataStoreFactory(DataStoreFactory dataStoreFactory) {
		this.dataStoreFactory = dataStoreFactory;
	}

	@GET
	@Path("/isInClusterMode")
	@Produces(MediaType.APPLICATION_JSON)
	public Result isInClusterMode(){
		WebApplicationContext ctxt = WebApplicationContextUtils.getWebApplicationContext(servletContext);
		//TODO move BEAN name from TCPCluster to IClusterNotifier then use it
		boolean isCluster = ctxt.containsBean("tomcat.cluster");
		return new Result(isCluster, "");
	}

	@GET
	@Path("/getLogLevel")
	@Produces(MediaType.APPLICATION_JSON)
	public LogSettings getLogSettings() 
	{

		PreferenceStore store = new PreferenceStore(RED5_PROPERTIES);
		store.setFullPath(RED5_PROPERTIES_PATH);

		LogSettings logSettings = new LogSettings();


		if (store.get(LOG_LEVEL) != null) {
			logSettings.setLogLevel(String.valueOf(store.get(LOG_LEVEL)));
		}

		return logSettings;
	}

	@GET
	@Path("/changeLogLevel/{level}")
	@Produces(MediaType.APPLICATION_JSON)
	public String changeLogSettings(@PathParam("level") String logLevel){

		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

		PreferenceStore store = new PreferenceStore(RED5_PROPERTIES);
		store.setFullPath(RED5_PROPERTIES_PATH);	

		
		if(logLevel.equals(LOG_LEVEL_ALL) || logLevel.equals(LOG_LEVEL_TRACE) 
				|| logLevel.equals(LOG_LEVEL_DEBUG) || logLevel.equals(LOG_LEVEL_INFO) 
				|| logLevel.equals(LOG_LEVEL_WARN)  || logLevel.equals(LOG_LEVEL_ERROR)
				|| logLevel.equals(LOG_LEVEL_OFF)) {

			rootLogger.setLevel(currentLevelDetect(logLevel));

			store.put(LOG_LEVEL, logLevel);

			LogSettings logSettings = new LogSettings();

			logSettings.setLogLevel(String.valueOf(logLevel));

		}

		return gson.toJson(new Result(store.save()));
	}

	public Level currentLevelDetect(String logLevel) {
		
		Level currentLevel;
		if( logLevel.equals(LOG_LEVEL_OFF)) {
			currentLevel = Level.OFF;
			return currentLevel;
		}
		if( logLevel.equals(LOG_LEVEL_ERROR)) {
			currentLevel = Level.ERROR;
			return currentLevel;
		}
		if( logLevel.equals(LOG_LEVEL_WARN)) {
			currentLevel = Level.WARN;
			return currentLevel;
		}
		if( logLevel.equals(LOG_LEVEL_DEBUG)) {
			currentLevel = Level.DEBUG;
			return currentLevel;
		}
		if( logLevel.equals(LOG_LEVEL_TRACE)) {
			currentLevel = Level.ALL;
			return currentLevel;
		}
		if( logLevel.equals(LOG_LEVEL_ALL)) {
			currentLevel = Level.ALL;
			return currentLevel;
		}
		else {
			currentLevel = Level.INFO;
			return currentLevel;
		}

	}

	@GET
	@Path("/getLogFile/{charCount}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getLogFile(@PathParam("charCount") int charCount,@QueryParam("logLocation") String logLocation) throws IOException 
	{
		JsonObject jsonObject = new JsonObject();
		String result = ""; 

		if(logLocation == null || logLocation.equals(""))
		{
			logLocation = "log/ant-media-server.log";
		}

		File file = new File(logLocation);

		if (!file.isFile()) {  
			result = "There are no registered logs yet";

			jsonObject.addProperty("logs", result);

			return jsonObject.toString();
		}        

		if (charCount>Integer.valueOf(SystemUtils.jvmFreeMemory("B", false))) {  

			result = "JVM Free Memory Not Enough for this query. Free Memory Size: "+ SystemUtils.jvmFreeMemory("B", false)+ " Char Count Size: " + charCount;

			jsonObject.addProperty("logs", result);

			return jsonObject.toString();	}

		else if (file.length()<charCount) {  

			result = "There are no many Chars in File";

			jsonObject.addProperty("logs", result);

			return jsonObject.toString();	}

		ByteArrayOutputStream ous = null;
		InputStream ios = null;
		try {
			byte[] buffer = new byte[1024];
			ous = new ByteArrayOutputStream();
			ios = new FileInputStream(file);

			ios.skip(file.length()-charCount);

			int read = 0;
			while ((read = ios.read(buffer)) != -1) {
				ous.write(buffer, 0, read);
			}
		}finally {
			try {
				if (ous != null)
					ous.close();
			} catch (IOException e) {
				logger.warn(e.toString());
			}

			try {
				if (ios != null)
					ios.close();
			} catch (IOException e) {
				logger.warn(e.toString());
			}
		}
		result =  ous.toString("UTF-8");

		jsonObject.addProperty("logs", result);

		return jsonObject.toString();
	}


}
