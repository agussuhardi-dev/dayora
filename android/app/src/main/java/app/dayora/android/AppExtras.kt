package app.dayora.android

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(val packageName:String,val label:String,val icon:Drawable?)
fun Repository.blockedPackages():Set<String> = context.getSharedPreferences("focus",0).getStringSet("blocked",emptySet()).orEmpty()
fun Repository.setBlockedPackages(value:Set<String>){context.getSharedPreferences("focus",0).edit().putStringSet("blocked",value).apply()}
fun Repository.installedApps():List<InstalledApp> = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter{it.packageName!=context.packageName && context.packageManager.getLaunchIntentForPackage(it.packageName)!=null}.map{InstalledApp(it.packageName,context.packageManager.getApplicationLabel(it).toString(),context.packageManager.getApplicationIcon(it))}.sortedBy{it.label.lowercase()}
