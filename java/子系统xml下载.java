
@Service("appVersionService")
public class AppVersionServiceImpl implements AppVersionService, IBaseService<AppVersion>{
	
	/**
	* @modify: {增加国际化功能} by maxuan 2017年04月01日 03:14:38
	*/
	@Override
	public String autoUpdateAppVersion(Map<String, Object> map) {//String appCode,String appType,String newInitVersion, String xmlString,String isI18n,String contextPath,String url
		
		Locale locale = I18nLocaleContext.getLocale();
		Map<String, String> resultMap = new HashMap<String, String>();//返回结果信息
		
		if(null == map || map.isEmpty()){
			resultMap.put("msg", I18nMessageContext.getMessage(locale, "apollo.java.ups.msg.paramCannotNull"));
			log.info("The required param Map is null or empty !");
			return StringTools.formatToJson(resultMap);
		}
		
		//解析map 信息
		String appCode = map.get(Constants.PARAM_APPCODE).toString();
		String appType = map.get(Constants.PARAM_APPYPE).toString();
		String newInitVersion = map.get(Constants.PARAM_NEWINITVERSION).toString();
		String xmlString = map.get(Constants.PARAM_XMLSTRING).toString();
		String isI18n = map.get(Constants.ISI18N)==null?"0":map.get(Constants.ISI18N).toString();
		String contextPath = map.get(Constants.CONTEXTPATH)==null?"":map.get(Constants.CONTEXTPATH).toString();
		String url = map.get(Constants.URL)==null?"":map.get(Constants.URL).toString();
		log.info("-------Remote automatic update business system database version,appCode{},appType{},newInitVersion{},isI18n{},contextPath{},url{}------",appCode,appType,newInitVersion,isI18n,contextPath,url);
		
		
		//判断是否需要更新升级
		String initAppVersion = "";//数据库中存在的版本号
		boolean isNeedUpdate = false;//是否需要升级
		AppVersion appVersion = getCurrentAppVersion(appType,"initVersion");
		if(null != appVersion){
			initAppVersion = appVersion.getInitVersion();
		}
		if(null == appVersion || "".equals(initAppVersion) || (null != initAppVersion && !initAppVersion.equals(newInitVersion)) ){
			isNeedUpdate = true;
		}
		log.info("current version：{}，subSystem version：{}，update：{}",initAppVersion,newInitVersion,isNeedUpdate);
		
		
        //是否更新xml
        boolean isUpdate = true;
		if(isNeedUpdate){
			//如果开启了国际化功能则需要下载国际化文件
			if("1".equals(isI18n)){
				log.info("--------(internationalization) business system {} get URL:{} start--------",appType,url);
				//根据appCode查询对应的子系统ip
                AppService appService = null;
                try {
                    appService = remoteAppSystemService.findAppServiceWithExtField(null,appCode,HTTPS);
                } catch (RemoteException e) {
                    log.error("Failed to call remoteAppSystemService.findAppServiceWithExtField ：{}",e);
                }
                StringBuffer ip = new StringBuffer();
				if(!ObjectUtils.isEmpty(appService)){
					ip = ip.append(HTTPS)
                            .append("://")
							.append(appService.getIp());
                            if(!StringUtils.isEmpty(appService.getExtFieldValue(HTTPS))){
                            ip.append(":")
                              .append(appService.getExtFieldValue(HTTPS));
                            }
							ip.append(contextPath).append(url).append("/");
				}
				//下载文件
				if(downloadFile(getDownloadVector(ip,appType))){
	                String fileName = "language."+appType;
	                LocalizedTextUtil.setReloadBundles(true);
	                LocalizedTextUtil.addDefaultResourceBundle(fileName);
					//该操作用于调用 LocalizedTextUtil 的 reloadBundles 方法
					LocalizedTextUtil.findDefaultText("apollo.db.appSystem.name.100001",Locale.getDefault());
	
					//处理并发写文件的问题：由于要在特定位置写入而位置却是不确定的所以不适用文件锁的方式
					synchronized (SyncContext.syncInObj){
						updateProperties(fileName);
						try {
							//IGNORE
							//暂时这样处理，延迟 10 秒
							Thread.sleep(10000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}else{
					//文件下载失败 ，不更新XML
					isUpdate = false;
				}
			}

			//解析xml、更新 应用配置数据库数据
		if (isUpdate) {
			......
		}else{
			......
		}
	}

	/**
	* 更新struts.properties文件的国际化配置
	* @author maxuan
	* @createDate 2017年05月09日 03:19:23
	* @param
	* @return
	*/
	public void updateProperties(String value){
		log.info("updateProperties start ,subSystem:{}",value);
		String path = this.getClass().getResource("/"+STRUTS).getPath();
		InputStream is = this.getClass().getResourceAsStream("/"+STRUTS);
		Properties p = new Properties();
		FileOutputStream os = null;
		try {
			p.load(is);
			//更新struts.properties文件
			if(!p.get(KEY).toString().contains(value)){
				p.setProperty(KEY,p.get(KEY)+","+value);
				os = new FileOutputStream(path);
				p.store(os,"modify");
			}
		} catch (IOException e) {
			log.error("struts.properties loading failed ！：{}",path);
		}finally {
			if(null!= os){
				try {
					os.close();
				} catch (IOException e) {
					log.error("File write failed！：{}",e);
				}
			}
			if(null!= is){
				try {
					is.close();
				} catch (IOException e) {
					log.error("File write failed！：{}",e);
				}
			}
		}
	}
	/**
	 * 构建下载地址URL集合
	 * @author maxuan
	 * @createDate 2017年03月28日 12:56:40
	 * @param ip
	 * @param appType
	 */
	public Vector<String> getDownloadVector(StringBuffer ip, String appType){
		Vector<String> downloadList = new Vector<String>();
		downloadList.add(ip+appType+ENGLISH_SUFFIX);
		downloadList.add(ip+appType+CHINESE_SUFFIX);
		return downloadList;
	}

	/**
	 * 通过 OkHttpClient 下载子系统相关的配置文件
	 * @author maxuan
	 * @createDate 2017年03月28日 12:56:21
	 * @param downloadList
	 * @return
	 */
	public boolean downloadFile(Vector<String> downloadList) {
		// 线程池
		ExecutorService pool = null;
		//循环下载
		try {
			pool = Executors.newCachedThreadPool();
			for (int i = 0; i < downloadList.size(); i++) {
				final String url = downloadList.get(i);
				Response response = null;
				String filename = getFilename(downloadList.get(i));
				log.debug("Downloading the " + (i+1) + " file ，the address {}",url);
				Future<Response> future = pool.submit(new Callable<Response>(){
					@Override
					public Response call() throws Exception {
						OkHttpClient okHttpClient = initOkHttpClient();
						Request request = new Request.Builder()
								.url(url)
								.get()
								.build();
						Response response = okHttpClient.newCall(request).execute();
						if(!response.isSuccessful()){
							log.warn("code{},httpUrl{},Location{},headers{}",response.code(),request.url(),response.headers().get("Location"));
						}
						return response;
					}
				});
				response = future.get();
				if(null != response){
					if(200 == response.code()){
						log.debug("Download completed. Response code:{}",response.code());
						// 写入文件
						writeFile(new BufferedInputStream(response.body().byteStream()), URLDecoder.decode(filename,"UTF-8"));
					}else{
						log.warn("Failed to download file :{},response code:{}",filename,response.code());
						return false;
					}
				}
			}
		} catch (Exception e) {
			log.error(" Failed to download subsystem international file：{}",e);
			return false;
		} finally {
			if (null != pool)
				pool.shutdown();
		}
		return true;
	}

	/**
	* 初始化 OkHttpClient
	* @author maxuan
	* @createDate 2017年04月16日 10:40:53
	* @param
	* @return
	*/
	private OkHttpClient initOkHttpClient() {
		OkHttpClient okHttpClient = new OkHttpClient();
		final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
			@Override
			public void checkClientTrusted(
					java.security.cert.X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(
					java.security.cert.X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		}};
		//忽略安全证书验证
		 SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("TLS");
			if(null == sslContext){
				return okHttpClient;
			}
			sslContext.init(null, trustAllCerts, new SecureRandom());
		} catch (Exception e) {
			log.error("sslContext init failed.{}",e);
		}
		if(null == sslContext){
			return okHttpClient;
		}
			okHttpClient = new OkHttpClient().newBuilder()
					.readTimeout(1, TimeUnit.MINUTES)
					.writeTimeout(1, TimeUnit.MINUTES)
					.connectTimeout(2, TimeUnit.MINUTES)
					.followRedirects(false)
					.sslSocketFactory(sslContext.getSocketFactory())
					.hostnameVerifier(new HostnameVerifier() {
						@Override
						public boolean verify(String s, SSLSession sslSession) {
							return true;
						}
					})
					.build();
		return okHttpClient;
	}

	/**
	 * 将子系统相关配置文件写入本地文件
	 * @author maxuan
	 * @createDate 2017年03月28日 12:57:27
	 * @param bufferedInputStream
	 * @param filename
	 */
	private void writeFile(BufferedInputStream bufferedInputStream, String filename) {
		//创建本地文件
		String url = this.getClass().getResource("/").getPath();
		File destFile = new File(url+"language"+File.separator+filename);
	if (!destFile.exists()) {
			try {
				destFile.createNewFile();
			} catch (IOException e) {
				log.error("Failed to create international file！:{}",destFile);
			}
				}
		if (!destFile.getParentFile().exists()) {
			destFile.getParentFile().mkdir();
		}
		BufferedOutputStream bufferedOutputStream = null;
		try {
		    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(destFile,false));
			byte[] b = new byte[1024];
			int len = 0;
			// 写入文件
			log.debug("------------ Start writing local file---------------");
			while ((len = bufferedInputStream.read(b, 0, b.length)) != -1) {
				log.debug("------------Writing bytes：{}---------------",len);
				bufferedOutputStream.write(b, 0, len);
			}
			//刷新缓冲区
			bufferedOutputStream.flush();
			log.debug("------------Writing local file completed.---------------");
		} catch (FileNotFoundException e) {
			log.error("There is no local file：{}",e);
		} catch (IOException e) {
			log.error("Failed to write local file：{}",e);
		} finally {
			try {
				if (null != bufferedOutputStream) {
					bufferedOutputStream.close();
				}
				if (null != bufferedInputStream)
					bufferedInputStream.close();
			} catch (IOException e) {
				log.error("Failed to close IO:{}",e);
			}
		}
	}

	/**
	 * 根据 URL获取文件名
	 * @author maxuan
	 * @createDate 2017年03月28日 12:58:04
	 * @param
	 * @return
	 */
	private String getFilename(String url) {
		return StringUtils.isEmpty(url) ? "" : url.substring(url.lastIndexOf("/") + 1,url.length());
	}

}
