package biz.atamai.myai
data class APIDataModel(
    var category: String = "",
    val action: String = "",
    val userInput: Map<String, String> = emptyMap(),
    val userSettings: Map<String, Map<String, Any>> = emptyMap(),
    val customerId: Int = 1
)
