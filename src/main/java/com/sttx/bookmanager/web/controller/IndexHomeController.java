package com.sttx.bookmanager.web.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.alibaba.fastjson.JSONObject;
import com.sttx.bookmanager.po.Book;
import com.sttx.bookmanager.po.TLog;
import com.sttx.bookmanager.po.User;
import com.sttx.bookmanager.po.VisitorProfile;
import com.sttx.bookmanager.service.IBaseMongoRepository;
import com.sttx.bookmanager.service.IBookService;
import com.sttx.bookmanager.service.ILogService;
import com.sttx.bookmanager.service.IResumeService;
import com.sttx.bookmanager.service.IVisitorProfileService;
import com.sttx.bookmanager.util.BookManagerBeanUtils;
import com.sttx.bookmanager.util.LogUtil;
import com.sttx.bookmanager.util.excel.ExportToExcelUtil;
import com.sttx.bookmanager.util.file.NfsFileUtils;
import com.sttx.bookmanager.util.map.AddressUtils;
import com.sttx.bookmanager.util.map.IPUtils;
import com.sttx.bookmanager.util.map.vo.IPAddressData;
import com.sttx.bookmanager.util.map.vo.IPAddressVo;
import com.sttx.bookmanager.util.pages.PagedResult;
import com.sttx.bookmanager.util.properties.PropertiesUtil;
import com.sttx.bookmanager.util.time.DateConvertUtils;
import com.sttx.bookmanager.util.tts.XunfeiLib;
import com.sttx.bookmanager.web.vo.LookResumeReq;
import com.sttx.bookmanager.web.vo.LookResumeResp;
import com.sttx.bookmanager.web.vo.TodayCountVo;

import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.BrowserType;
import eu.bitwalker.useragentutils.DeviceType;
import eu.bitwalker.useragentutils.Manufacturer;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.RenderingEngine;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;

@Controller
public class IndexHomeController {
  private static final Logger log = LoggerFactory.getLogger(IndexHomeController.class);
  @Autowired
  private ILogService logService;
  @Autowired
  private IResumeService resumeService;
  @Resource
  private IBaseMongoRepository baseMongoRepository;
  @Autowired
  private IBookService bookService;
  @Autowired
  private IVisitorProfileService visitorProfileService;

  @RequestMapping(value = "/visitors", method = RequestMethod.GET)
  public void visitors(HttpServletResponse response) {

    List<VisitorProfile> visitors = visitorProfileService.visitors();
    String jsonString = JSONObject.toJSONString(visitors);
    String formatAsJSON = LogUtil.formatAsJSON(jsonString);
    try {
      response.setCharacterEncoding("GBK");
      response.getWriter().write(formatAsJSON);
    } catch (Exception e) {
      log.error("visitors error:{}", e);
    }

  }

  @RequestMapping(value = "/lookResume", method = RequestMethod.POST)
  @CrossOrigin
  public @ResponseBody LookResumeResp lookResume(@RequestBody LookResumeReq req, HttpServletRequest request) {

    log.info("查看网站主页 req:{}", JSONObject.toJSONString(req));

    log.info("浏览器的正式名称:{}", req.getAppName());
    log.info("浏览器的版本号:{}", req.getAppVersion());
    log.info("返回用户浏览器是否启用了cookie:{}", req.getCookieEnabled());
    log.info("返回用户计算机的cpu的型号:{}", req.getCpuClass());
    log.info("浏览器正在运行的操作系统平台:{}", req.getPlatform());
    log.info("浏览器的产品名（IE没有）:{}", req.getProduct());
    log.info("浏览器正在运行的操作系统，其中可能有CPU的信息（IE没有）:{}", req.getOscpu());
    log.info("关于浏览器更多信息（IE没有）:{}", req.getProductSub());
    log.info("userAgent:{}", req.getUserAgent());
    log.info("返回一个UserProfile对象，它存储用户的个人信息（火狐没有）:{}", req.getUserProfile());
    String ipAddr = IPUtils.getIpAddr(request);
    log.info("用户ip地址:{}", ipAddr);
    String userAddress = "";
    IPAddressVo ipAddressVo = AddressUtils.getIPAddressVo(ipAddr);
    if (ipAddressVo == null || !"0".equals(ipAddressVo.getCode())) {
      userAddress = "搜不到你,请尝试刷新";
    } else {
      IPAddressData data = ipAddressVo.getData();
      String area = data.getArea();
      String country = data.getCountry();
      String province = data.getRegion();
      String city = data.getCity();
      String isp = data.getIsp();
      if(StringUtils.isBlank(area)){
        userAddress = province + "," + city + "," + country + "," + isp;
      }else{
        userAddress = area + "," + province + "," + city + "," + country + "," + isp;
      }
    }
    log.info("通过ip解析用户地址:{}", userAddress);

    //
    LookResumeResp resp = new LookResumeResp();
    log.info("查看网站主页 http://www.shopbop.ink/");

    try {
      // save
      VisitorProfile visitorProfile = BookManagerBeanUtils.copyBean(req, VisitorProfile.class);
      visitorProfile.setCreateTime(DateConvertUtils.format(new Date(), DateConvertUtils.DATE_TIME_FORMAT));
      visitorProfile.setIp(ipAddr);
      visitorProfile.setAddress(userAddress);
      
      /**
       * 保存用户浏览器信息
       */
      String agentStr = request.getHeader("user-agent");
      log.info("用户浏览器信息agentStr:{}",agentStr);
      UserAgent agent = UserAgent.parseUserAgentString(agentStr);
      // 浏览器
      Browser browser = agent.getBrowser()==null?Browser.UNKNOWN:agent.getBrowser();
      // 浏览器版本
      Version version = agent.getBrowserVersion();
      // 系统
      OperatingSystem os = agent.getOperatingSystem()==null?OperatingSystem.UNKNOWN:agent.getOperatingSystem();
      /**
       * 保存字段
       */
      // 浏览器类型
      BrowserType browserType = browser.getBrowserType();
      // 浏览器名称和版本
      String browserAndVersion = String.format("%s-%s", browser.getGroup().getName(), version==null?"未知":version.getVersion());
      // 浏览器厂商
      Manufacturer manufacturer = browser.getManufacturer();
      // 浏览器引擎
      RenderingEngine renderingEngine = browser.getRenderingEngine();
      // 系统名称
      String sysName = os.getName();
      // 产品系列
      OperatingSystem operatingSystem = os.getGroup();
      // 生成厂商
      Manufacturer sysManufacturer = os.getManufacturer();
      // 设备类型
      DeviceType deviceType = os.getDeviceType();
   // 浏览器信息
      visitorProfile.setBrowserAndVersion(browserAndVersion);
      visitorProfile.setBrowserType(browserType.name());
      visitorProfile.setManufacturer(manufacturer.name());
      visitorProfile.setRenderingEngine(renderingEngine.name());
      visitorProfile.setSysName(sysName);
      visitorProfile.setOperatingSystem(operatingSystem.name());
      visitorProfile.setSysManufacturer(sysManufacturer.name());
      visitorProfile.setDeviceType(deviceType.name());
      
      int insert = visitorProfileService.insert(visitorProfile);
      log.info("保存用户信息:{}", insert);
      //
      TLog tLog = new TLog();
      PagedResult<TLog> pages = logService.selectLogPages(tLog, 1, 1);
      Long totalcount = logService.selectLogSumCount();
      TodayCountVo todayCount = logService.todayCount();
      long totalPathCount = logService.totalPathCount("lookResume");
      //
      resp.setTodayCount(todayCount.getTodayCount());
      resp.setTodayVisitorCount(todayCount.getTodayVisitorCount());
      resp.setTotalcount(totalcount);
      resp.setResumeCount(totalPathCount);
      resp.setTotalVisitorCount(pages.getPages());
      resp.setFlag(true);
    } catch (Exception e) {
      log.error("查看网站主页异常:{}", e);
      resp.setFlag(false);
      resp.setMsg(e.getMessage());
    }
    resp.setMsg("操作成功");
    log.info("查看网站主页 resp:{}", JSONObject.toJSONString(resp));
    return resp;
  }

  @RequestMapping("/indexHome")
  public String indexHome(Model model, HttpServletRequest request, ModelAndView modelAndView, Integer pageNo,
    Integer pageSize) {
    String realPath = request.getServletContext().getRealPath("resources/ehcache.xml");
    log.info(">>>>>>>>>realPath:{}", realPath);
    // return "forward:/book/selectBookPages";
    TLog tLog = new TLog();
    PagedResult<TLog> pages = logService.selectLogPages(tLog, pageNo, pageSize);
    Long totalcount = logService.selectLogSumCount();
    TodayCountVo todayCount = logService.todayCount();
    String url = request.getRequestURI();
    pages.setUrl(url);
    model.addAttribute("pages", pages);
    model.addAttribute("totalcount", totalcount);
    model.addAttribute("todayCount", todayCount);
    log.info(">>>>>>>>>pages getTotal:{}", JSONObject.toJSON(pages.getTotal()));
    // return "redirect:job/m2/index.html";
    return "ipLog";
  }

  @RequestMapping("/")
  public String indexResume(Model model, HttpServletRequest request, ModelAndView modelAndView) {

    // ResumeVo resumeVo = resumeService.findResumeVo();

    // log.info("缓存中的信息 resumeVo.getImageVo:{}",
    // LogUtil.formatLog(resumeVo.getImageVo()));

    // model.addAttribute("resumeVo", resumeVo);
    // 此方法加重浏览器解析base64 照片负担更慢了=-=

    // return "/job/m2/resume";
    // return "forward:/book/selectBookPages";
    return "forward:/indexHome";
  }

  @RequestMapping("/resume")
  public String resume() {
    return "job/m2/resume";
  }

  @RequestMapping(value = "/indexHomeForIp", method = RequestMethod.GET)
  public String indexHomeForIp(String userIp, Model model, HttpServletRequest request, ModelAndView modelAndView,
    Integer pageNo, Integer pageSize) {
    // return "forward:/book/selectBookPages";
    PagedResult<TLog> pages = logService.selectLogPagesForIp(userIp, pageNo, pageSize);
    Long totalcount = logService.selectLogSumCount();
    String url = request.getRequestURI();
    pages.setStrWhere("userIp=" + userIp);
    pages.setUrl(url);
    model.addAttribute("pages", pages);
    model.addAttribute("totalcount", totalcount);
    return "ipLog";
  }

  @RequestMapping("/downloadResumeDocx")
  public String downloadResumeDocx(Model model, HttpServletResponse response, HttpServletRequest request)
    throws Exception {

    String fileName = "北京-Java开发工程师-陈超允.docx";
    fileName = new String(fileName.getBytes("UTF-8"), "iso8859-1");
    response.reset();// 去除空白行
    response.setHeader("Content-Disposition", "attachment;filename=" + fileName);// 指定下载的文件名
    response.setContentType("application/vnd.ms-excel");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-cache");
    response.setDateHeader("Expires", 0);
    InputStream bis = null;
    try {
      String nfsFileName = NfsFileUtils.getNfsUrl() + "resume/docx/chenchaoyun-resume.docx";
      log.info("nfsFileName:{}", nfsFileName);
      bis = NfsFileUtils.readNfsFile2Stream(nfsFileName);
      ServletOutputStream outputStream = response.getOutputStream();
      IOUtils.copy(bis, outputStream);
    } catch (Exception e) {
      log.error("要下载的文件不存在", e);
      request.setAttribute("msg", "要下载的文件不存在" + e.getMessage());
      return "forward:/error/msg.jsp";
    } finally {
    }
    return null;
  }

  @RequestMapping("/exportBookListExcel/{pageNo}/{pageSize}")
  public void exportBookListExcel(Book book, String loginName, HttpServletResponse response,
    @PathVariable("pageNo") Integer pageNo, @RequestParam(value = "userId", required = false) String userId,
    @PathVariable("pageSize") Integer pageSize) throws IOException {
    User user = new User();
    user.setLoginName(loginName);
    book.setUser(user);
    if (!"".equals(userId) && userId != null) {
      book.setUserId(userId);
    }
    /* 分页 */
    PagedResult<Book> pages = bookService.selectBookPages(book, pageNo, pageSize);
    List<Book> bookList = pages.getDataList();
    String filePath = PropertiesUtil.getFilePath("uploadFilePath.properties", "book.ImgPath");
    for (int i = 0; i < bookList.size(); i++) {
      Book b = bookList.get(i);
      String bookImg = b.getBookImg();
      String idstr = StringUtils.substringAfterLast(bookImg, "/");
      InputStream in = baseMongoRepository.getInputStreamById(new ObjectId(idstr));
      BufferedImage img = ImageIO.read(in);
      b.setBookImage(img);
    }

    try {
      ExportToExcelUtil.out(response, bookList);
      return;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @RequestMapping("/testImg")
  public String testImg(HttpServletRequest request, Model model) {
    String nfsFileName = NfsFileUtils.getNfsUrl() + "ad.jpg";
    String imageBase64Str = NfsFileUtils.getImageBase64Str(nfsFileName);
    Book book = new Book();
    book.setBookAuthor("chenchaoyun");
    book.setBookImg(imageBase64Str);
    model.addAttribute("book", book);
    return "testImg";
  }
  // @RequestMapping("/ttsData/{data}")
  // public void ttsData(HttpServletRequest request,@PathVariable("data")String
  // data,HttpServletResponse response) throws Exception {
  // request.setCharacterEncoding("UTF-8");//解决乱码
  //
  // //换成你在讯飞申请的APPID
  // SpeechUtility.createUtility("appid=5ac38223");
  //
  // //合成监听器
  // SynthesizeToUriListener synthesizeToUriListener =
  // XunfeiLib.getSynthesize();
  //
  // String fileName=XunfeiLib.getFileName("tts_test.pcm");
  // XunfeiLib.delDone(fileName);
  //
  // //1.创建SpeechSynthesizer对象
  // SpeechSynthesizer mTts= SpeechSynthesizer.createSynthesizer( );
  // //2.合成参数设置，详见《MSC Reference Manual》SpeechSynthesizer 类
  // mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");//设置发音人
  // mTts.setParameter(SpeechConstant.SPEED, "50");//设置语速，范围0~100
  // mTts.setParameter(SpeechConstant.PITCH, "50");//设置语调，范围0~100
  // mTts.setParameter(SpeechConstant.VOLUME, "50");//设置音量，范围0~100
  //
  // //3.开始合成
  // //设置合成音频保存位置（可自定义保存位置），默认保存在“./tts_test.pcm”
  // mTts.synthesizeToUri(data,fileName ,synthesizeToUriListener);
  //
  // //设置最长时间
  // int timeOut=30;
  // int star=0;
  //
  // //校验文件是否生成
  // while(!XunfeiLib.checkDone(fileName)){
  //
  // try {
  // Thread.sleep(1000);
  // star++;
  // if(star>timeOut){
  // throw new Exception("合成超过"+timeOut+"秒！");
  // }
  // } catch (Exception e) {
  // // TODO 自动生成的 catch 块
  // e.printStackTrace();
  // break;
  // }
  //
  // }
  //
  // this.sayPlay(fileName, request, response);
  // }

  /**
   * 将音频内容输出到请求中
   * 
   * @param fileName
   * @param request
   * @param response
   */
  private void sayPlay(String fileName, HttpServletRequest request, HttpServletResponse response) {

    // 输出 wav IO流
    try {

      response.setHeader("Content-Type", "audio/mpeg");
      File file = new File(fileName);
      int len_l = (int)file.length();
      byte[] buf = new byte[2048];
      FileInputStream fis = new FileInputStream(file);
      OutputStream out = response.getOutputStream();

      // 写入WAV文件头信息
      out.write(XunfeiLib.getWAVHeader(len_l, 8000, 2, 16));

      len_l = fis.read(buf);
      while (len_l != -1) {
        out.write(buf, 0, len_l);
        len_l = fis.read(buf);
      }
      out.flush();
      out.close();
      fis.close();

      // 删除文件和清除队列信息
      XunfeiLib.delDone(fileName);
      file.delete();
    } catch (Exception e) {
      System.out.println(e);
    }

  }

}
