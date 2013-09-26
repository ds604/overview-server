package views.json.DocumentSetUser

import org.overviewproject.tree.orm.DocumentSetUser
import play.api.libs.json.Json.toJson
import play.api.libs.json.JsValue

object index {
  def apply(documentSetUsers: Iterable[DocumentSetUser]): JsValue = {
    val emails : Iterable[String] = documentSetUsers.map(_.userEmail)
    val emailJsons = emails.map({ email: String => toJson(Map("email" -> email)) })

    toJson(Map(
      "viewers" -> toJson(emailJsons)
    ))
  }
}
