package de.lernplan.osdb2

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

 data class Task(val id:String,val category:String,val title:String,val details:String,val minutes:Int,val url:String)
 data class StudyDay(val day:Int,val week:Int,val phase:String,val date:String,val focus:String,val goal:String,val totalMinutes:Int,val tasks:List<Task>)

class MainActivity: ComponentActivity(){
 private val requestPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
 override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState)
  createChannel()
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  val prefs=getSharedPreferences("lernplan",MODE_PRIVATE)
  if(!prefs.contains("start_date")) prefs.edit().putString("start_date","2026-10-08").apply()
  ReminderScheduler.schedule(this)
  setContent { LernplanApp() }
 }
 private fun createChannel(){ if(Build.VERSION.SDK_INT>=26){ val nm=getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel("lernplan_daily","Tägliche Lern-Erinnerung",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Erinnert an die heutigen ÖSD-B2-Aufgaben"}) } }
}

object PlanRepository {
 fun load(context:Context):List<StudyDay>{ val raw=context.assets.open("plan.json").bufferedReader().use{it.readText()}; val arr=JSONArray(raw); return (0 until arr.length()).map { o-> val d=arr.getJSONObject(o); val ta=d.getJSONArray("tasks"); val tasks=(0 until ta.length()).map{ i->val t=ta.getJSONObject(i); Task(t.getString("id"),t.getString("category"),t.getString("title"),t.getString("details"),t.getInt("minutes"),t.getString("url"))}; StudyDay(d.getInt("day"),d.getInt("week"),d.getString("phase"),d.getString("date"),d.getString("focus"),d.getString("goal"),d.getInt("totalMinutes"),tasks) } }
}
object ReminderScheduler {
 fun schedule(c:Context){ val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val i=Intent(c,ReminderReceiver::class.java); val pi=PendingIntent.getBroadcast(c,101,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,c.getSharedPreferences("lernplan",Context.MODE_PRIVATE).getInt("hour",8));set(Calendar.MINUTE,c.getSharedPreferences("lernplan",Context.MODE_PRIVATE).getInt("minute",0));set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1)}; try { if(Build.VERSION.SDK_INT>=23 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) } catch(_:Exception){am.set(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)} }
}
class ReminderReceiver:BroadcastReceiver(){ override fun onReceive(c:Context, i:Intent?){ val prefs=c.getSharedPreferences("lernplan",Context.MODE_PRIVATE); val start=runCatching{LocalDate.parse(prefs.getString("start_date","2026-10-08"))}.getOrDefault(LocalDate.now()); val n=(ChronoUnit.DAYS.between(start,LocalDate.now()).toInt()+1).coerceIn(1,70); val day=runCatching{PlanRepository.load(c).first{it.day==n}}.getOrNull(); val nm=c.getSystemService(NotificationManager::class.java); val text=day?.let{"Tag ${it.day}: ${it.focus} · ${it.totalMinutes} Minuten"}?:"Öffne deinen Lernplan und setze dein Lernen fort."; val notification=if(Build.VERSION.SDK_INT>=26) Notification.Builder(c,"lernplan_daily") else Notification.Builder(c); notification.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Zeit für dein ÖSD-B2-Training!").setContentText(text).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,1,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)); nm.notify(2001,notification.build()); ReminderScheduler.schedule(c) } }
class BootReceiver:BroadcastReceiver(){ override fun onReceive(c:Context,i:Intent?){ReminderScheduler.schedule(c)} }

@Composable fun LernplanApp(){ val context= LocalContext.current; val prefs=remember{context.getSharedPreferences("lernplan",Context.MODE_PRIVATE)}; val days= remember{PlanRepository.load(context)}; var page by remember{mutableStateOf("Heute")}; var refresh by remember{mutableIntStateOf(0)}; var selectedDay by remember{mutableIntStateOf(1)}; var reminderHour by remember{mutableIntStateOf(prefs.getInt("hour",8))}; var reminderMinute by remember{mutableIntStateOf(prefs.getInt("minute",0))}; val start=runCatching{LocalDate.parse(prefs.getString("start_date","2026-10-08"))}.getOrDefault(LocalDate.now()); val today=(ChronoUnit.DAYS.between(start,LocalDate.now()).toInt()+1).coerceIn(1,70); val completed= remember(refresh){prefs.all.filterKeys{it.startsWith("done_")}.filterValues{it==true}.keys}; val total=days.sumOf{it.tasks.size}; val done=completed.size
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF174A7E),secondary=Color(0xFF2F7D6D),background=Color(0xFFF5F7FA),surface=Color.White)){ Scaffold(bottomBar={ NavigationBar { listOf("Heute" to "Heute","Plan" to "Lernplan","Fortschritt" to "Fortschritt","Einstellungen" to "Einstellungen").forEach{ (key,label)->NavigationBarItem(selected=page==key,onClick={page=key},icon={Text(when(key){"Heute"->"☀";"Plan"->"▦";"Fortschritt"->"↗";else->"⚙"},fontSize=18.sp)},label={Text(label,fontSize=11.sp)}) } } }) { pad-> Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(pad).padding(horizontal=16.dp)){ when(page){"Heute"->{ val d=days.first{it.day==today}; Header("ÖSD B2 Lernplan","Dein täglicher Lernbegleiter"); Spacer(Modifier.height(12.dp)); SummaryCard(d,done,total); Spacer(Modifier.height(12.dp)); Text("Heutige Aufgaben",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("Markiere jede Aufgabe, sobald du sie erledigt hast.",fontSize=13.sp,color=Color.Gray); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(d.tasks){t->TaskCard(t,completed.contains("done_${d.day}_${t.id}")){checked->prefs.edit().putBoolean("done_${d.day}_${t.id}",checked).apply();refresh++}}; item{GoalCard(d.goal)} } }
 "Plan"->{Header("Lernplan","Alle 70 Tage im Überblick"); LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(days){d->Card(Modifier.fillMaxWidth().clickable{selectedDay=d.day;page="Tag"},shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Tag ${d.day} · Woche ${d.week}",fontWeight=FontWeight.Bold);Text(d.focus,fontSize=14.sp);Text("${d.totalMinutes} Min. · ${d.phase}",fontSize=12.sp,color=Color.Gray)};Text(if(d.tasks.all{completed.contains("done_${d.day}_${it.id}")})"✓" else "›",fontSize=24.sp,color=Color(0xFF174A7E))}}} } }
 "Tag"->{val d=days.first{it.day==selectedDay}; Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={page="Plan"}){Text("‹ Lernplan")};Spacer(Modifier.weight(1f))}; Header("Tag ${d.day}",d.focus); LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(d.tasks){t->TaskCard(t,completed.contains("done_${d.day}_${t.id}")){v->prefs.edit().putBoolean("done_${d.day}_${t.id}",v).apply();refresh++}};item{GoalCard(d.goal)}}}
 "Fortschritt"->{Header("Fortschritt","Deine Entwicklung auf einen Blick"); Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(18.dp)){Text("Gesamtfortschritt",fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress={if(total==0)0f else done.toFloat()/total},modifier=Modifier.fillMaxWidth()); Text("$done von $total Aufgaben erledigt",modifier=Modifier.padding(top=8.dp)); Text("${(done*100/total)} %",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color(0xFF174A7E))}}; Spacer(Modifier.height(12.dp)); days.forEach{d->val n=d.tasks.count{completed.contains("done_${d.day}_${it.id}")}; Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("Tag ${d.day}",modifier=Modifier.width(70.dp)); LinearProgressIndicator(progress={n.toFloat()/d.tasks.size},modifier=Modifier.weight(1f));Text("$n/${d.tasks.size}",modifier=Modifier.padding(start=8.dp),fontSize=12.sp)}} }
 else->{Header("Einstellungen","Passe deinen Lernplan an"); var startText by remember{mutableStateOf(prefs.getString("start_date","2026-10-08")?:"2026-10-08")}; var hourText by remember{mutableStateOf(prefs.getInt("hour",8).toString())}; var minuteText by remember{mutableStateOf(prefs.getInt("minute",0).toString().padStart(2,'0'))}; Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text("Startdatum",fontWeight=FontWeight.Bold);OutlinedTextField(startText,{startText=it},label={Text("JJJJ-MM-TT")},singleLine=true);Text("Erinnerungszeit",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp));Row{OutlinedTextField(hourText,{hourText=it.filter{c->c.isDigit()}.take(2)},Modifier.weight(1f),label={Text("Stunde (0–23)")},singleLine=true);Spacer(Modifier.width(8.dp));OutlinedTextField(minuteText,{minuteText=it.filter{c->c.isDigit()}.take(2)},Modifier.weight(1f),label={Text("Minute (0–59)")},singleLine=true)};Button(onClick={runCatching{val date=LocalDate.parse(startText);val h=hourText.toInt().coerceIn(0,23);val m=minuteText.toInt().coerceIn(0,59);prefs.edit().putString("start_date",date.toString()).putInt("hour",h).putInt("minute",m).apply(); ReminderScheduler.schedule(context); refresh++}.fold({true},{false})},modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text("Einstellungen speichern")}; Text("Hinweis: Für zuverlässige Erinnerungen müssen Benachrichtigungen erlaubt sein. Je nach Android-Version können Akkuoptimierung und Energiesparmodus die Zustellung verzögern.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))}}}
 } } } }
}
@Composable fun Header(title:String,subtitle:String){Column(Modifier.padding(top=16.dp,bottom=6.dp)){Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold,color=Color(0xFF17324D));Text(subtitle,fontSize=14.sp,color=Color.Gray)}}
@Composable fun SummaryCard(d:StudyDay,done:Int,total:Int){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF174A7E))){Column(Modifier.fillMaxWidth().padding(18.dp)){Text("TAG ${d.day} VON 70",color=Color.White.copy(alpha=.8f),fontSize=12.sp,fontWeight=FontWeight.Bold);Text(d.focus,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White);Spacer(Modifier.height(6.dp));Text("${d.totalMinutes} Minuten Lernzeit · Woche ${d.week}",color=Color.White);Text(d.phase,color=Color.White.copy(alpha=.8f),fontSize=12.sp,modifier=Modifier.padding(top=5.dp))}}}
@Composable fun TaskCard(t:Task,checked:Boolean,onChecked:(Boolean)->Unit){val context=LocalContext.current;var expanded by remember{mutableStateOf(false)}; Card(shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked,onChecked);Column(Modifier.weight(1f).clickable{expanded=!expanded}){Text(t.category,fontSize=11.sp,color=Color(0xFF2F7D6D),fontWeight=FontWeight.Bold);Text(t.title,fontWeight=FontWeight.SemiBold);Text("${t.minutes} Minuten",fontSize=12.sp,color=Color.Gray)};TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Weniger" else "Details")}};if(expanded){HorizontalDivider();Text(t.details,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.padding(top=10.dp));if(t.url.isNotBlank()){TextButton(onClick={runCatching{val intent=Intent(Intent.ACTION_VIEW,Uri.parse(t.url));context.startActivity(intent)}.getOrNull()}){Text("Material öffnen ↗")}}}}} }
@Composable fun GoalCard(goal:String){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF3EF)),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(14.dp)){Text("Tagesziel",fontWeight=FontWeight.Bold,color=Color(0xFF205D4D));Text(goal,fontSize=13.sp,lineHeight=19.sp,color=Color(0xFF205D4D))}}}
