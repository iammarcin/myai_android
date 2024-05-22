package biz.atamai.myai
data class APIDataModel(
    var category: String = "",
    val action: String = "",
    val userInput: Map<String, Any> = emptyMap(),
    val assetInput: List<Any> = emptyList(),
    val userSettings: Map<String, Map<String, Any>> = emptyMap(),
    val customerId: Int = 1,
)
