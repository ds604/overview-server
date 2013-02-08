package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.OverviewDocumentSet

trait PublicDocumentSetController extends Controller {
  
  def index = AuthorizedAction(anyUser) { implicit request =>
    val publicDocumentSets = OverviewDocumentSet.findPublic
    
    Ok(views.html.PublicDocumentSet.index(publicDocumentSets))
  }

}


object PublicDocumentSetController extends PublicDocumentSetController 