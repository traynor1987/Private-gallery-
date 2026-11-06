package uk.co.traynor.privategallery.home2

import android.content.Context
import androidx.compose.runtime.*
import java.math.BigDecimal
import uk.co.traynor.privategallery.core.calculator.*
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** Calculation state belongs only to the captured Space. Transient input is never restored from intents. */
internal class CalculatorData(private val context:SpaceContext) {
    private val prefs=context.getSharedPreferences(CalculatorPreferences.NAME,Context.MODE_PRIVATE)
    private val initial=prefs.all.also{CalculatorPreferences.validate(it)}
    var radians by mutableStateOf(initial["angle"]=="RAD");private set
    var scientific by mutableStateOf(initial["scientific"] as? Boolean?:false);private set
    var historyEnabled by mutableStateOf(initial["history-enabled"] as? Boolean?:true);private set
    var memory by mutableStateOf((initial["memory"] as? String)?.let(CalculatorEngine::storedNumber));private set
    var history by mutableStateOf((initial["history"] as? String)?.let(CalculatorPreferences::decode)?:emptyList());private set
    var expression by mutableStateOf("");private set
    var result by mutableStateOf<BigDecimal?>(null);private set
    var error by mutableStateOf<String?>(null);private set
    private var evaluated=false
    fun current()=context.current()
    private fun write(action:android.content.SharedPreferences.Editor.()->Unit):Boolean {
        if(!current())return false
        return runCatching{check(prefs.edit().apply(action).commit());true}.getOrElse{error="Calculator data could not be saved. Existing saved data is preserved.";false}
    }
    fun input(value:String){if(!current())return;if(value.length>CalculatorEngine.MAX_EXPRESSION){error="Expression limit is 512 characters";return};expression=value;result=null;evaluated=false;error=null}
    fun append(value:String){
        if(!current())return
        val base=if(evaluated&&value.firstOrNull() !in listOf('+','-','*','/','^','%','!'))""else if(evaluated)result?.let(CalculatorEngine::canonical).orEmpty() else expression
        input(base+value)
    }
    fun clear(){input("")}
    fun delete(){input(expression.dropLast(1))}
    fun sign(){if(expression.isNotBlank())input(if(isWholeNegation(expression))expression.substring(2,expression.length-1)else "-($expression)")else input("-")}
    private fun isWholeNegation(value:String):Boolean {
        if(!value.startsWith("-(")||!value.endsWith(')'))return false
        var depth=0
        for(index in 1 until value.length){
            when(value[index]){'('->depth++;')'->depth--}
            // The opening pair must enclose every term, not merely the first negative term.
            if(depth==0)return index==value.lastIndex
        }
        return false
    }
    fun evaluate():Boolean {
        if(!current()||expression.isBlank())return false
        return try {
            val number=CalculatorEngine.evaluate(expression,radians)
            val entry=CalculatorPreferences.Entry(expression,CalculatorEngine.canonical(number),radians)
            val next=(listOf(entry)+history).take(CalculatorPreferences.MAX_HISTORY)
            if(historyEnabled&&!write{putString("history",CalculatorPreferences.encode(next))})return false
            if(historyEnabled)history=next
            result=number;evaluated=true;error=null;true
        }catch(failure:IllegalArgumentException){result=null;error=failure.message?:"Check the expression";false}
    }
    fun settings(radians:Boolean=this.radians,scientific:Boolean=this.scientific,historyEnabled:Boolean=this.historyEnabled){
        if(write{putString("angle",if(radians)"RAD"else "DEG");putBoolean("scientific",scientific);putBoolean("history-enabled",historyEnabled);if(!historyEnabled)putString("history",CalculatorPreferences.encode(emptyList()))}){
            if(this.radians!=radians){result=null;evaluated=false}
            this.radians=radians;this.scientific=scientific;this.historyEnabled=historyEnabled;if(!historyEnabled)history=emptyList();error=null
        }
    }
    fun clearHistory(){if(write{putString("history",CalculatorPreferences.encode(emptyList()))})history=emptyList()}
    fun memory(action:String){
        if(!current())return
        if(action=="MR"){memory?.let{append("(${CalculatorEngine.canonical(it)})")};return}
        if(action=="MC"){if(write{remove("memory")})memory=null;return}
        try {
            val value=result?:CalculatorEngine.evaluate(expression,radians)
            val next=when(action){"MS"->value;"M+"->CalculatorEngine.bounded((memory?:BigDecimal.ZERO).add(value,CalculatorEngine.PRECISION));"M−"->CalculatorEngine.bounded((memory?:BigDecimal.ZERO).subtract(value,CalculatorEngine.PRECISION));else->throw IllegalArgumentException("Unknown memory action")}
            if(write{putString("memory",CalculatorEngine.canonical(next))}){memory=next;error=null}
        }catch(failure:IllegalArgumentException){error=failure.message}
    }
    fun recall(entry:CalculatorPreferences.Entry){if(current()){input(entry.expression());settings(radians=entry.radians())}}
    fun retireInput(){expression="";result=null;error=null;evaluated=false}
}
