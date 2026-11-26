package app.dayora.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusBlockRulesTest {
 @Test fun blocksOnlyActiveSelectedForegroundApps() {
  val youtube="com.google.android.youtube"
  val own="app.dayora.android"
  val blocked=setOf(youtube,own)
  assertTrue(shouldBlockPackage(true,youtube,own,blocked))
  assertFalse(shouldBlockPackage(false,youtube,own,blocked))
  assertFalse(shouldBlockPackage(true,own,own,blocked))
  assertFalse(shouldBlockPackage(true,"",own,blocked))
  assertFalse(shouldBlockPackage(true,"com.android.launcher",own,blocked))
 }
}

