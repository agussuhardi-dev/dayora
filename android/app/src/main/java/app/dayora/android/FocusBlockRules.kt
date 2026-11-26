package app.dayora.android

fun shouldBlockPackage(active:Boolean,pkg:String,ownPackage:String,blocked:Set<String>)=active&&pkg.isNotBlank()&&pkg!=ownPackage&&pkg in blocked
fun foregroundPackage(previous:String,eventPackage:String)=if(eventPackage=="com.android.systemui")previous else eventPackage
fun delayedBlockPackage(expected:String,current:String)=expected.takeIf{it==current}
