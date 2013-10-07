package com.douban.base

import android.content
import android.net.ConnectivityManager
import android.view.{ViewGroup, View, MenuItem}
import com.douban.book._
import com.douban.common._
import java.lang.Thread.UncaughtExceptionHandler
import org.scaloid.common._
import scala.concurrent._
import ExecutionContext.Implicits.global
import collection.JavaConverters._
import scala.collection.mutable
import android.widget._
import android.graphics.drawable.Drawable
import java.net.URL
import java.io.{FileOutputStream, File, InputStream}
import android.graphics.{Bitmap, BitmapFactory}
import android.content.{DialogInterface, Context}
import android.telephony.TelephonyManager
import scala.language.implicitConversions
import scala.language.reflectiveCalls
import android.os.Bundle
import org.scaloid.support.v4.{SFragment, SListFragment, SFragmentActivity}
import android.support.v4.app.Fragment
import android.app.{ProgressDialog, ActionBar}
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu
import scala.util.Failure
import org.scaloid.common.LoggerTag
import scala.util.Success
import com.douban.common.AccessTokenResult

/**
 * Copyright by <a href="http://crazyadam.net"><em><i>Joseph J.C. Tang</i></em></a> <br/>
 * Email: <a href="mailto:jinntrance@gmail.com">jinntrance@gmail.com</a>
 * @author joseph
 * @since 4/21/13 5:14 PM
 * @version 1.0
 */
trait Douban {
  type V = {
    def findViewById(id: Int): View
  }
  protected val countPerPage = 12

  implicit def ctx: DoubanActivity = getThisActivity

  protected val rootView: V

  def getThisActivity: DoubanActivity

  def setViewValue[T <: V](id: Int, value: String, holder: T = rootView, notification: String = "") {
    value.trim match {
      case "" => holder.findViewById(id) match {
        case view: View => view.setVisibility(View.GONE)
        case _ =>
      }
      case value: String => holder.findViewById(id) match {
        case view: TextView => view.setText(value)
        case rating: RatingBar => rating.setNumStars(value.toInt)
        case img: ImageView if value != "URL" => loadImage(value, img, notification)
        case _ =>
      }
    }
  }

  def batchSetValues[T <: V](m: Map[Int, Any], values: Map[String, String], holder: T = rootView, separator: String = "/") {
    m.par.foreach {
      case (id, key: String) => setViewValue(id, values.getOrElse(key, ""), holder)
      case (id, (key: String, format: String)) => setViewValue(id, format.format(values.getOrElse(key, "")), holder)
      case (id, l: List[String]) => setViewValue(id, l.map(values.getOrElse(_, "")).filter(_ != "").mkString(separator), holder)
      case (id, (urlKey: String, (notifyField: String, format: String))) => setViewValue(id, values.getOrElse(urlKey, "URL"), holder, format.format(values.getOrElse(notifyField, ""))) //TODO add support
    }
  }

  @inline def hideWhenEmpty(m: (Int, String)) {
    hideWhenEmpty(m._1, m._2)
  }

  def hideWhenEmpty(resId: Int, value: String, holder: V = rootView) = value match {
    case null | "" => holder.findViewById(resId) match {
      case v: View => v.setVisibility(View.GONE)
      case _ =>
    }
    case _ =>
  }

  def hideWhen(resId: Int, condition: Boolean, holder: V = rootView) = if (condition) {
    holder.findViewById(resId) match {
      case v: View => v.setVisibility(View.GONE)
      case _ =>
    }
  }

  def displayWhen(resId: Int, condition: Boolean, holder: V = rootView) = {
    holder.findViewById(resId) match {
      case v: View => v.setVisibility(if (condition) View.VISIBLE else View.GONE)
      case _ =>
    }
  }

  /**
   *
   * @return the visible one
   */
  def toggleBetween(view1: Int, view2: Int, holder: V = rootView): View = {
    val v1 = holder.findViewById(view1)
    val v2 = holder.findViewById(view2)
    //    play()
    if (v1.getVisibility == View.GONE) {
      v2.setVisibility(View.GONE)
      v1.setVisibility(View.VISIBLE)
      v1
    } else {
      v1.setVisibility(View.GONE)
      v2.setVisibility(View.VISIBLE)
      v2
    }
  }

  def toggleBackGround(firstOneAsBackground: Boolean, viewId: Int, res: (Int, Int), holder: V = rootView): Boolean = toggleBackGround(firstOneAsBackground, holder.findViewById(viewId), res)

  def toggleBackGround(firstOneAsBackground: Boolean, view: View, res: (Int, Int)): Boolean = {
    val chosen = if (firstOneAsBackground) res._1 else res._2
    view match {
      case img: ImageView => img.setImageResource(chosen)
      case txt: TextView => txt.setBackgroundResource(chosen)
      case _ =>
    }
    !firstOneAsBackground
  }

  @inline def isOnline = {
    val activeNetwork = ctx.getSystemService(content.Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager].getActiveNetworkInfo
    activeNetwork.isConnectedOrConnecting
  }

  @inline def usingWIfi = {
    val activeNetwork = ctx.getSystemService(content.Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager].getActiveNetworkInfo
    activeNetwork.getType == ConnectivityManager.TYPE_WIFI
  }

  @inline def using2G: Boolean = {
    import TelephonyManager._
    val t = ctx.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager].getNetworkType match {
      case NETWORK_TYPE_GPRS | NETWORK_TYPE_EDGE | NETWORK_TYPE_CDMA | NETWORK_TYPE_1xRTT | NETWORK_TYPE_IDEN => "2G"
      case _ => "3G"
    }
    t == "2G"
  }

  def BitmapFromUrl(url: String) = {
    BitmapFactory.decodeStream(new URL(url).getContent.asInstanceOf[InputStream])
  }

  def loadImageWithTitle(url: String, resId: Int, title: String, holder: V = rootView, updateCache: Boolean = false): Unit = holder.findViewById(resId) match {
    case img: ImageView => loadImage(url, img, ctx.getString(R.string.load_img_fail, title), updateCache)
    case _ =>
  }

  def loadImage(url: String, img: ImageView, notification: String = "", updateCache: Boolean = false): Unit = {
    val cacheFile = new File(ctx.getExternalCacheDir, url.dropWhile(_ != '/'))
    if (!updateCache && cacheFile.exists()) {
      val b = Drawable.createFromPath(cacheFile.getAbsolutePath)
      runOnUiThread(img.setImageDrawable(b))
    } else future {
      BitmapFromUrl(url)
    } onComplete {
      case Success(b) => {
        runOnUiThread(img.setImageBitmap(b))
        if (!cacheFile.exists() && cacheFile.getParentFile.mkdirs) {
          cacheFile.createNewFile()
        }
        val out = new FileOutputStream(cacheFile, false)
        b.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.close()
      }
      case Failure(b) => toast(notification)
    }
  }
}

trait DoubanActivity extends SFragmentActivity with Douban {
  implicit val loggerTag = LoggerTag("DoubanBook")

  def findFragment[T <: Fragment](fragmentId: Int): T = fragmentManager.findFragmentById(fragmentId) match {
    case f: Fragment => f.asInstanceOf[T]
    case _ => new Fragment().asInstanceOf[T]
  }

  Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
    def uncaughtException(thread: Thread, ex: Throwable) {
      ex match {
        case exception: DoubanException if exception.tokenExpired =>
          handle(Auth.getTokenByFresh(get(Constant.refreshTokenString), Constant.apiKey, Constant.apiSecret)
            , (t: Option[AccessTokenResult]) => {
              if (None != t) updateToken(t.get)
              else {
                put(Constant.accessTokenString, "")
                toast(R.string.relogin_needed)
              }
            })
        case e:Exception => e.printStackTrace()
        case _ =>
      }
      toast(ex.getMessage)
      ex.printStackTrace()
    }
  })
  lazy val slidingMenu = {
    val sm = new SlidingMenu(this)
    sm.setMode(SlidingMenu.LEFT)
    sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN)
    //    sm.setShadowWidthRes(R.dimen.shadow_width)
    //    sm.setShadowDrawable(R.drawable.shadow)
    sm.setBehindOffsetRes(R.dimen.margin_huge) //TODO
    sm.setFadeDegree(0.35f)
    sm.attachToActivity(this, SlidingMenu.SLIDING_WINDOW)
    sm.setMenu(R.layout.menu)
    getActionBar.setDisplayHomeAsUpEnabled(true)
    sm
  }
  override implicit val ctx: DoubanActivity = this
  /*  override def startActivity(intent: Intent) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK))
      }*/
  lazy val fragmentManager = getSupportFragmentManager

  override def getThisActivity = this

  override lazy val rootView: V = this

  def handle[R](result: => R, handler: (R) => Unit) {
    future {
      result
    } onComplete {
      case Success(t) => handler(t)
      case Failure(m) => debug(m.getMessage)
    }
  }

  def replaceActionBar(layoutId: Int = R.layout.header, title: String = getString(R.string.app_name)) {
    getActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM)
    getActionBar.setCustomView(layoutId)
    setWindowTitle(title)
  }

  def setWindowTitle(title: CharSequence) = setViewValue(R.id.title, title.toString)

  def setWindowTitle(title: Int) = setViewValue(R.id.title, title.toString)

  def put(key: String, value: Any) {
    val edit = defaultSharedPreferences.edit()
    value match {
      case l: List[String] => edit.putStringSet(key, l.toSet.asJava).commit()
      case i: Any => edit.putString(key, value.toString).commit()
    }
  }

  lazy val currentUserId = get[Long](Constant.userIdString)

  @inline def get[T](key: String): T = defaultSharedPreferences.getAll.get(key).asInstanceOf[T]

  @inline def contains(key: String): Boolean = defaultSharedPreferences.contains(key) && get[String](key).nonEmpty

  @inline def notifyNetworkState() {
    if (!isOnline) toast(R.string.notify_offline)
  }

 @inline def getAccessToken = {
    if (!contains(Constant.accessTokenString))
      startActivity(SIntent[LoginActivity])
    get(Constant.accessTokenString)
  }

  def back(i: MenuItem) {
    onBackPressed()
  }

  def back(v: View) {
    onBackPressed()
  }

  @inline def isAuthenticated = {
    !get[String](Constant.accessTokenString).isEmpty
  }

  protected def updateToken(t: AccessTokenResult) {
    put(Constant.accessTokenString, t.access_token)
    put(Constant.refreshTokenString, t.refresh_token)
    put(Constant.userIdString, t.douban_user_id)
  }

  private var _sp: ProgressDialog = null

  def waitToLoad(cancel: => Unit = {
    finishedLoading(); getThisActivity.finish()
  })(implicit ctx: Context) = {

    _sp = spinnerDialog("请稍候", "数据加载中…")
    _sp.setCanceledOnTouchOutside(true)
    _sp.setCancelable(true)
    _sp.setOnCancelListener(new DialogInterface.OnCancelListener() {
      def onCancel(p1: DialogInterface) {
        cancel
      }
    })
    _sp.show()
    _sp
  }

  def finishedLoading() {
    if (null != _sp) {
      _sp.dismiss()
    }
  }
}

trait DoubanListFragment[T <: DoubanActivity] extends SListFragment with Douban {
  lazy implicit val loggerTag = getThisActivity.loggerTag

  override def getThisActivity: T = getActivity.asInstanceOf[T]

  override lazy val rootView: View = getView

  def addArguments(args: Bundle): Fragment = {
    this.setArguments(args)
    this
  }

  def popup(img: View) {
    img match {
      case image: ImageView => startActivity[ImagePopupActivity]
    }
  }
}

trait DoubanFragment[T <: DoubanActivity] extends SFragment with Douban {
  lazy implicit val loggerTag = getThisActivity.loggerTag

  override def getThisActivity: T = getActivity.asInstanceOf[T]

  override lazy val rootView: View = getView

  def addArguments(args: Bundle): Fragment = {
    this.setArguments(args)
    this
  }
}

case class DBundle(b: Bundle = new Bundle()) {
  def put[T](key: String, value: T): Bundle = {
    value match {
      case s: String => b.putString(key, s)
      case i: Int => b.putInt(key, i)
      case l: Long => b.putLong(key, l)
      case s: Serializable => b.putSerializable(key, s)
      case _ =>
    }
    b
  }

   def put(bd: Bundle): Bundle = {
    b.putAll(bd)
    b
  }
}

class ItemAdapter[B <: Any](layoutId: Int, mapping: Map[Int, Any], data: collection.mutable.Buffer[Map[String, String]] = mutable.Buffer[Map[String, String]](), load: => Unit = {})(implicit activity: DoubanActivity) extends BaseAdapter {
  var total = Long.MaxValue
  var count = 0
  val list: java.util.List[B] = new java.util.ArrayList[B]()

  def getCount: Int = count

  def getItem(index: Int): Map[String, String] = data(index)

  def getBean(index: Int): B = list.get(index)

  def getItemId(position: Int): Long = position

  def addResult(total: Long, loadedSize: Int, items: java.util.List[B]) {
    this.total = total
    this.count += loadedSize
    list.addAll(items)
    data ++= items.map(beanToMap(_))
  }

  def getView(position: Int, view: View, parent: ViewGroup): View = {
    val convertView = if (null != view) view else activity.getLayoutInflater.inflate(layoutId, null)
    activity.batchSetValues(mapping, data(position), convertView)
    if (position + 2 >= count && count < total) load
    convertView
  }
}
