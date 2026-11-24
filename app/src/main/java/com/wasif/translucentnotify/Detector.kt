    /** Open the page where the detector can be switched on: its own page if possible, else the list. */
    fun openEnablePage(c: Context) {
        val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
        if (Build.VERSION.SDK_INT >= 31) {
            val opened = runCatching {
                val details = Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                details.putExtra(Intent.EXTRA_COMPONENT_NAME, component(c))
                details.addFlags(newTask)
                c.startActivity(details)
            }.isSuccess
            if (opened) return
        }
        runCatching { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(newTask)) }
    }
