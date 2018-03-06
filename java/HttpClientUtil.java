package vision.apollo.imputil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.DefaultCookieSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson.JSONObject;

public class HttpClientUtil {
	
	private static final Logger log = LoggerFactory.getLogger(HttpClientUtil.class);
	// httpClient连接超时时间
	private static int HTTP_CLIENT_CONNECTION_TIMEOUT = 3000;
	private static int HTTP_CLIENT_SO_TIMEOUT = 5000;
	private static int bufferSize = 1024;
	private static volatile HttpClientUtil instance;
	private ConnectionConfig connConfig;
	private SocketConfig socketConfig;
	private ConnectionSocketFactory plainSF;
	private SSLContext sslContext;
	private LayeredConnectionSocketFactory sslSF;
	private Registry<ConnectionSocketFactory> registry;
	private PoolingHttpClientConnectionManager connManager;
	private volatile HttpClient client;
	private volatile BasicCookieStore cookieStore;
	public static final String defaultEncoding = "utf-8";
	private static final ThreadLocal<HttpRequestBase> threadLocal = new ThreadLocal<HttpRequestBase>();
	
	private static List<NameValuePair> paramsConverter(Map<String, String> params) {
		List<NameValuePair> nvps = new LinkedList<NameValuePair>();
		Set<Entry<String, String>> paramsSet = params.entrySet();
		for (Entry<String, String> paramEntry : paramsSet) {
			nvps.add(new BasicNameValuePair(paramEntry.getKey(), paramEntry.getValue()));
		}
		return nvps;
	}
	
	// 读取内容
	public static String readStream(InputStream in, String encoding) {
		if (in == null) {
			return null;
		}
		try {
			InputStreamReader inReader = null;
			if (encoding == null) {
				inReader = new InputStreamReader(in, defaultEncoding);
			} else {
				inReader = new InputStreamReader(in, encoding);
			}
			char[] buffer = new char[bufferSize];
			int readLen = 0;
			StringBuffer sb = new StringBuffer();
			while ((readLen = inReader.read(buffer)) != -1) {
				sb.append(buffer, 0, readLen);
			}
			inReader.close();
			return sb.toString();
		} catch (IOException e) {
			log.error("There was an error reading the contents of the return ", e);
		}
		return null;
	}
	
	private HttpClientUtil() {
		// 设置连接参数
		connConfig = ConnectionConfig.custom().setCharset(Charset.forName(defaultEncoding)).build();
		socketConfig = SocketConfig.custom().setSoTimeout(HTTP_CLIENT_SO_TIMEOUT).build();
		RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory> create();
		plainSF = new PlainConnectionSocketFactory();
		registryBuilder.register("http", plainSF);
		// 指定信任密钥存储对象和连接套接字工厂
		try {
			sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy() {
				
				@Override
				public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					return true;
				}
			}).build();
			sslSF = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
			registryBuilder.register("https", sslSF);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		registry = registryBuilder.build();
		// 设置连接管理器
		connManager = new PoolingHttpClientConnectionManager(registry);
		connManager.setDefaultConnectionConfig(connConfig);
		connManager.setDefaultSocketConfig(socketConfig);
		// 指定cookie存储对象
		cookieStore = new BasicCookieStore();
		// 构建客户端
		client = HttpClients.custom().setDefaultCookieStore(cookieStore).setRedirectStrategy(new LaxRedirectStrategy()).setConnectionManager(connManager).build();
	}
	
	public static HttpClientUtil getInstance() {
		synchronized (HttpClientUtil.class) {
			if (HttpClientUtil.instance == null) {
				instance = new HttpClientUtil();
			}
			return instance;
		}
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public InputStream doGet(String url) throws UnsupportedOperationException, IOException, URISyntaxException {
		HttpResponse response = this.doGet(url, null);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public InputStream doGet(String url, int readTimeout, int connectionTimeOut) throws UnsupportedOperationException, IOException, URISyntaxException {
		HttpResponse response = this.doGet(url, null, readTimeout, connectionTimeOut);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public String doGetForString(String url) throws UnsupportedOperationException, IOException, URISyntaxException {
		return HttpClientUtil.readStream(this.doGet(url), null);
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public InputStream doGetForStream(String url, Map<String, String> queryParams) throws UnsupportedOperationException, IOException, URISyntaxException {
		HttpResponse response = this.doGet(url, queryParams);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public String doGetForString(String url, Map<String, String> queryParams) throws UnsupportedOperationException, IOException, URISyntaxException {
		return HttpClientUtil.readStream(this.doGetForStream(url, queryParams), null);
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public String doGetForString(String url, int readTimeout, int connectionTimeOut) throws UnsupportedOperationException, IOException, URISyntaxException {
		return HttpClientUtil.readStream(this.doGet(url, readTimeout, connectionTimeOut), null);
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public InputStream doGetForStream(String url, Map<String, String> queryParams, int readTimeout, int connectionTimeOut) throws UnsupportedOperationException, IOException, URISyntaxException {
		HttpResponse response = this.doGet(url, queryParams, readTimeout, connectionTimeOut);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * get请求
	 * @throws IOException
	 * @throws UnsupportedOperationException
	 * @throws URISyntaxException
	 */
	public String doGetForString(String url, Map<String, String> queryParams, int readTimeout, int connectionTimeOut) throws UnsupportedOperationException, IOException, URISyntaxException {
		return HttpClientUtil.readStream(this.doGetForStream(url, queryParams, readTimeout, connectionTimeOut), null);
	}
	
	/**
	 * 基本的Get请求
	 * @param url 请求url
	 * @param queryParams 请求头的查询参数
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws ClientProtocolException
	 */
	public HttpResponse doGet(String url, Map<String, String> queryParams) throws URISyntaxException, ClientProtocolException, IOException {
		return doGet(url, queryParams, HTTP_CLIENT_SO_TIMEOUT, HTTP_CLIENT_CONNECTION_TIMEOUT);
	}
	
	public HttpResponse doGet(String url, Map<String, String> queryParams, int readTimeout, int connectionTimeOut) throws URISyntaxException, ClientProtocolException, IOException {
		HttpGet gm = new HttpGet();
		threadLocal.set(gm);
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(readTimeout).setConnectTimeout(connectionTimeOut).build();
		gm.setConfig(requestConfig);
		URIBuilder builder = new URIBuilder(url);
		// 填入查询参数
		if (queryParams != null && !queryParams.isEmpty()) {
			builder.setParameters(HttpClientUtil.paramsConverter(queryParams));
		}
		gm.setURI(builder.build());
		HttpResponse httpResponse = client.execute(gm);
		return httpResponse;
	}
	
	/**
	 * Gets the redirect location.
	 *
	 * @param url the url
	 * @param location the location
	 * @param isSecure the isSecure
	 * @return the redirect location
	 * @throws URISyntaxException the URI syntax exception
	 * @throws ClientProtocolException the client protocol exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public int getRedirectLocation(String url, String[] location, boolean[] isSecure) throws URISyntaxException, ClientProtocolException, IOException {
		HttpGet gm = new HttpGet();
		threadLocal.set(gm);
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT).build();
		gm.setConfig(requestConfig);
		URIBuilder builder = new URIBuilder(url);
		gm.setURI(builder.build());
		HttpClientContext ctx = HttpClientContext.create();
		HttpResponse httpResponse = client.execute(gm, ctx);
		if (location != null && location.length > 0) {
			location[0] = ctx.getTargetHost().toHostString();
		}
		if (httpResponse.getLastHeader("Strict-Transport-Security") != null) {
			isSecure[0] = true;
		}
		return httpResponse.getStatusLine().getStatusCode();
	}
	
	/**
	 * Do get without redirect.
	 *
	 * @param url the url
	 * @return the int
	 * @throws URISyntaxException the URI syntax exception
	 * @throws ClientProtocolException the client protocol exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public int doGetWithoutRedirect(String url) throws URISyntaxException, ClientProtocolException, IOException {
		HttpGet gm = new HttpGet();
		threadLocal.set(gm);
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT).setRedirectsEnabled(false).build();
		gm.setConfig(requestConfig);
		URIBuilder builder = new URIBuilder(url);
		gm.setURI(builder.build());
		HttpResponse httpResponse = client.execute(gm);
		return httpResponse.getStatusLine().getStatusCode();
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public InputStream doPostForStream(String url, Map<String, String> queryParams) throws ClientProtocolException, URISyntaxException, IOException {
		HttpResponse response = this.doPost(url, queryParams, null, null);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public String doPostForString(String url, Map<String, String> queryParams) throws ClientProtocolException, URISyntaxException, IOException {
		return HttpClientUtil.readStream(this.doPostForStream(url, queryParams), null);
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public InputStream doPostForStream(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams) throws ClientProtocolException, URISyntaxException, IOException {
		HttpResponse response = this.doPost(url, queryParams, formParams, jsonParams);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public String doPostRetString(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams) throws ClientProtocolException, URISyntaxException, IOException {
		return HttpClientUtil.readStream(this.doPostForStream(url, queryParams, formParams, jsonParams), null);
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public InputStream doPostForStream(String url, Map<String, String> queryParams, int readTimeout, int connectionTimeOut) throws ClientProtocolException, URISyntaxException, IOException {
		HttpResponse response = this.doPost(url, queryParams, null, null, readTimeout, connectionTimeOut);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public String doPostForString(String url, Map<String, String> queryParams, int readTimeout, int connectionTimeOut) throws ClientProtocolException, URISyntaxException, IOException {
		return HttpClientUtil.readStream(this.doPostForStream(url, queryParams, readTimeout, connectionTimeOut), null);
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public InputStream doPostForStream(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams, int readTimeout, int connectionTimeOut)
	        throws ClientProtocolException, URISyntaxException, IOException {
		HttpResponse response = this.doPost(url, queryParams, formParams, jsonParams, readTimeout, connectionTimeOut);
		return response != null?response.getEntity().getContent():null;
	}
	
	/**
	 * post请求
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 */
	public String doPostRetString(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams, int readTimeout, int connectionTimeOut)
	        throws ClientProtocolException, URISyntaxException, IOException {
		return HttpClientUtil.readStream(this.doPostForStream(url, queryParams, formParams, jsonParams, readTimeout, connectionTimeOut), null);
	}
	
	/**
	 * 基本的Post请求
	 * @param url 请求url
	 * @param queryParams 请求头的查询参数
	 * @param formParams post表单的参数
	 * @param jsonParams json格式的参数
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws ClientProtocolException
	 */
	public HttpResponse doPost(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams) throws URISyntaxException, ClientProtocolException, IOException {
		return doPost(url, queryParams, formParams, jsonParams, HTTP_CLIENT_SO_TIMEOUT, HTTP_CLIENT_CONNECTION_TIMEOUT);
	}
	
	public HttpResponse doPost(String url, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> jsonParams, int readTimeout, int connectionTimeOut)
	        throws URISyntaxException, ClientProtocolException, IOException {
		HttpPost pm = new HttpPost();
		threadLocal.set(pm);
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(readTimeout).setConnectTimeout(connectionTimeOut).build();
		pm.setConfig(requestConfig);
		URIBuilder builder = new URIBuilder(url);
		// 填入查询参数
		if (queryParams != null && !queryParams.isEmpty()) {
			builder.setParameters(HttpClientUtil.paramsConverter(queryParams));
		}
		pm.setURI(builder.build());
		// 填入表单参数
		if (formParams != null && !formParams.isEmpty()) {
			pm.setEntity(new UrlEncodedFormEntity(HttpClientUtil.paramsConverter(formParams), defaultEncoding));
		}
		if (jsonParams != null && !jsonParams.isEmpty()) {
			JSONObject json = new JSONObject();
			for (Entry<String, String> entry : jsonParams.entrySet()) {
				json.put(entry.getKey(), entry.getValue());
			}
			StringEntity entity = new StringEntity(json.toString());
			entity.setContentEncoding(defaultEncoding);
			entity.setContentType("application/json");
			pm.setEntity(entity);
		}
		HttpResponse httpResponse = client.execute(pm);
		return httpResponse;
	}
	
	public String uploadFile(String url, InputStream in, String fileName) throws URISyntaxException, ClientProtocolException, IOException {
		HttpPost pm = new HttpPost();
		threadLocal.set(pm);
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(HTTP_CLIENT_SO_TIMEOUT).setConnectTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT).build();
		pm.setConfig(requestConfig);
		URIBuilder builder = new URIBuilder(url);
		pm.setURI(builder.build());
		MultipartEntityBuilder multi = MultipartEntityBuilder.create().setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		multi.addBinaryBody("file", in, ContentType.DEFAULT_BINARY, fileName);
		pm.setEntity(multi.build());
		HttpResponse httpResponse = client.execute(pm);
		return readStream(httpResponse.getEntity().getContent(), null);
	}
	
	/**
	 * 获取当前Http客户端状态中的Cookie
	 * @param domain 作用域
	 * @param port 端口 传null 默认80
	 * @param path Cookie路径 传null 默认"/"
	 * @param useSecure Cookie是否采用安全机制 传null 默认false
	 * @return
	 */
	public Map<String, Cookie> getCookie(String domain, Integer port, String path, Boolean useSecure) {
		if (domain == null) {
			return null;
		}
		if (port == null) {
			port = 80;
		}
		if (path == null) {
			path = "/";
		}
		if (useSecure == null) {
			useSecure = false;
		}
		List<Cookie> cookies = cookieStore.getCookies();
		if (cookies == null || cookies.isEmpty()) {
			return null;
		}
		CookieOrigin origin = new CookieOrigin(domain, port, path, useSecure);
		DefaultCookieSpec cookieSpec = new DefaultCookieSpec();
		Map<String, Cookie> retVal = new HashMap<String, Cookie>();
		for (Cookie cookie : cookies) {
			if (cookieSpec.match(cookie, origin)) {
				retVal.put(cookie.getName(), cookie);
			}
		}
		return retVal;
	}
	
	/**
	 * 批量设置Cookie
	 * @param cookies cookie键值对图
	 * @param domain 作用域 不可为空
	 * @param path 路径 传null默认为"/"
	 * @param useSecure 是否使用安全机制 传null 默认为false
	 * @return 是否成功设置cookie
	 */
	public boolean setCookie(Map<String, String> cookies, String domain, String path, Boolean useSecure) {
		synchronized (cookieStore) {
			if (domain == null) {
				return false;
			}
			if (path == null) {
				path = "/";
			}
			if (useSecure == null) {
				useSecure = false;
			}
			if (cookies == null || cookies.isEmpty()) {
				return true;
			}
			Set<Entry<String, String>> set = cookies.entrySet();
			String key = null;
			String value = null;
			for (Entry<String, String> entry : set) {
				key = entry.getKey();
				value = entry.getValue();//应该是忘记给value赋值了--修改converty缺陷，
				if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
					throw new IllegalArgumentException("cookies key and value both can not be empty");
				}
				BasicClientCookie cookie = new BasicClientCookie(key, value);
				cookie.setDomain(domain);
				cookie.setPath(path);
				cookie.setSecure(useSecure);
				cookieStore.addCookie(cookie);
			}
			return true;
		}
	}
	
	/**
	 * 设置单个Cookie
	 * @param key Cookie键
	 * @param value Cookie值
	 * @param domain 作用域 不可为空
	 * @param path 路径 传null默认为"/"
	 * @param useSecure 是否使用安全机制 传null 默认为false
	 * @return 是否成功设置cookie
	 */
	public boolean setCookie(String key, String value, String domain, String path, Boolean useSecure) {
		Map<String, String> cookies = new HashMap<String, String>();
		cookies.put(key, value);
		return setCookie(cookies, domain, path, useSecure);
	}
	
	public void releaseConnection() {
		if (threadLocal.get() != null) {
			threadLocal.get().releaseConnection();
			threadLocal.remove();
		}
	}
}
