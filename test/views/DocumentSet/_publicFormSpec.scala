package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry

import org.overviewproject.test.Specification
import helpers.FakeOverviewDocumentSet
import org.specs2.specification.Scope
import models.OverviewDocumentSet

class _publicFormSpec extends Specification {
  
  trait ViewContext extends Scope {
    def documentSet: OverviewDocumentSet
    
    def body: String = _publicForm(documentSet).body
    lazy val j = jerry(body)
    def $(selector: java.lang.String) = j.$(selector)
  }  

  trait PrivateDocumentSetContext extends ViewContext {
    def documentSet = FakeOverviewDocumentSet()
  }
  
  trait PublicDocumentSetContext extends ViewContext {
    def documentSet = FakeOverviewDocumentSet(isPublic = true)
  }
  
  "DocumentSet._publicForm" should {
    
    "be a FORM with an action pointing to update api" in new PrivateDocumentSetContext {
      $("form").length must be equalTo(1)
      
      $("form").attr("action") must be equalTo("/documentsets/%d?X-HTTP-Method-Override=PUT".format(documentSet.id))
    }
    
    "have an unchecked checkbox for private document sets" in new PrivateDocumentSetContext {
      $("form :checkbox").length must be equalTo(1)
      $("form :checkbox").attr("checked") must beNull
    }
    
    "have a checked checkbox for public document sets" in new PublicDocumentSetContext {
      $("form :checkbox").attr("checked") must be equalTo("true")
    }
    
    "have a hidden input after checkbox" in new PrivateDocumentSetContext {
      val hiddenInput = $("form input").get(1) 
      hiddenInput.getAttribute("value") must be equalTo("false")
    }
    
    "have a label" in new PublicDocumentSetContext {
      $("form label").text must be equalTo("views.DocumentSet._publicForm.label")
    }
  }
}