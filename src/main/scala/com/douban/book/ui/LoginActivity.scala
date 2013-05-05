package com.douban.book
package ui

import android.os.Bundle
import android.webkit.{WebChromeClient, WebView, WebViewClient}
import android.graphics.Bitmap
import org.scaloid.common._
import com.douban.base.{DoubanActivity, Constant}
import com.douban.common._
import Auth._
import android.view.{LayoutInflater, MenuItem, Menu}
import android.content.Context
import android.widget.ImageView
import android.view.animation.AnimationUtils

class LoginActivity extends DoubanActivity {
  private[this] var refreshItem:MenuItem=null
  override def onCreate(b: Bundle) {
    super.onCreate(b)
    setContentView(R.layout.login)
    find[WebView](R.id.authView).setWebViewClient(new DoubanWebViewClient)
  }

  def refresh(i:MenuItem){
    val iv=getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].inflate(R.layout.refresh,null).asInstanceOf[ImageView]
    iv.startAnimation(AnimationUtils.loadAnimation(this,R.anim.refresh))
    refreshItem.setActionView(iv)
    find[WebView](R.id.authView).loadUrl(getAuthUrl(Constant.apiKey, scope = Constant.scope))
  }

  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater.inflate(R.menu.login,menu)
    refreshItem=menu.findItem(R.id.menu_refresh)
    refresh(refreshItem)
    super.onCreateOptionsMenu(menu)
  }

 private class DoubanWebViewClient extends WebViewClient {
    override def onPageStarted(view: WebView, redirectedUrl: String, favicon: Bitmap) {
      if (redirectedUrl.startsWith(redirect_url)) {
        if (redirectedUrl.contains("error=")) toast(R.string.login_failed)
        else {
          toast(R.string.waiting_for_auth)
          handle( {
            Auth.getTokenByCode(extractCode(redirectedUrl), Constant.apiKey, Constant.apiSecret)
          } , (t:Option[AccessTokenResult])=>{
              if (None == t) toast(R.string.login_failed)
              else {
                updateToken(t.get)
                Req.init(t.get.access_token)
                toast(R.string.login_successfully)
              }
          })
          view.stopLoading()
          finish()
        }
      }
      else super.onPageStarted(view, redirectedUrl, favicon)
    }

    override def onPageFinished(view: WebView, url: String) {
      super.onPageFinished(view, url)
      refreshItem.getActionView.clearAnimation()
      refreshItem.setActionView(null)
    }
  }
}
