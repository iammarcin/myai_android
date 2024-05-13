package biz.atamai.myai
data class TestModel(
    val action: String = "",
    val userInput: Map<String, String> = emptyMap(),
    val userSettings: Map<String, String> = emptyMap(),
    val customerId: Int = 1
)
