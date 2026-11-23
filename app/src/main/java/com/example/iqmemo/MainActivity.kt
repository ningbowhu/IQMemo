package com.example.iqmemo

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.MotionEvent
import android.graphics.Rect
import kotlin.math.abs
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.example.iqmemo.databinding.ActivityMainBinding
import com.example.iqmemo.ui.note.NoteEditActivity
import com.example.iqmemo.ui.note.NoteListFragment
import com.example.iqmemo.ui.recording.RecordingActivity
import com.example.iqmemo.speech.SpeechLanguage
import com.example.iqmemo.speech.SpeechPreferences
import com.example.iqmemo.ui.notebook.NotebookEditActivity
import com.example.iqmemo.ui.notebook.NotebookListFragment
import com.example.iqmemo.ui.search.SearchResultsFragment
import com.example.iqmemo.ui.search.SearchViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var notebooksSelected = false
    private val searchModel: SearchViewModel by viewModels()
    private var searchActive = false
    private var restoreSearch = false
    private var searchItem: MenuItem? = null
    private lateinit var searchBack: OnBackPressedCallback
    private var swipeCandidate = false
    private var swipeConsumed = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f

    companion object {
        fun widgetIntent(context: Context) = Intent(context, MainActivity::class.java)
            .putExtra("open_memos", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        restoreSearch = savedInstanceState?.getBoolean("search_active") == true
        searchBack = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { searchItem?.collapseActionView() }
        }
        onBackPressedDispatcher.addCallback(this, searchBack)

        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, NoteListFragment(), "memos")
            .add(R.id.fragmentContainer, NotebookListFragment(), "notebooks")
            .commitNow()
        binding.sectionTabs.addOnButtonCheckedListener { _, id, checked ->
            if (checked) showSection(id == R.id.tabNotebooks)
        }
        showSection(savedInstanceState?.getBoolean("notebooks_selected") == true)

        binding.btnNewText.setOnClickListener {
            noteListFragment()?.openPlainTextRecorder() ?: NoteEditActivity.start(this)
        }

        binding.btnNewVoice.setOnClickListener {
            noteListFragment()?.openPlainVoiceRecorder() ?: RecordingActivity.start(this)
        }
        binding.btnNewNotebook.setOnClickListener { startActivity(NotebookEditActivity.intent(this)) }
    }

    private fun showSection(notebooks: Boolean) {
        notebooksSelected = notebooks
        val memos = supportFragmentManager.findFragmentByTag("memos") ?: return
        val pages = supportFragmentManager.findFragmentByTag("notebooks") ?: return
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.findFragmentByTag("search")?.let { hide(it) }
            if (notebooks) { hide(memos); show(pages) } else { hide(pages); show(memos) }
        }.commitNow()
        val target = if (notebooks) R.id.tabNotebooks else R.id.tabMemos
        if (binding.sectionTabs.checkedButtonId != target) binding.sectionTabs.check(target)
        binding.btnNewText.visibility = if (notebooks) View.GONE else View.VISIBLE
        binding.btnNewVoice.visibility = if (notebooks) View.GONE else View.VISIBLE
        binding.btnNewNotebook.visibility = if (notebooks) View.VISIBLE else View.GONE
        onPrepareOptionsMenu(binding.toolbar.menu)
    }

    private fun showSearch(active: Boolean) {
        searchActive = active
        searchBack.isEnabled = active
        binding.bottomButtonBar.visibility = if (active) View.GONE else View.VISIBLE
        if (active) {
            supportFragmentManager.beginTransaction().apply {
                supportFragmentManager.findFragmentByTag("memos")?.let { hide(it) }
                supportFragmentManager.findFragmentByTag("notebooks")?.let { hide(it) }
                val results = supportFragmentManager.findFragmentByTag("search")
                if (results == null) add(R.id.fragmentContainer, SearchResultsFragment(), "search") else show(results)
            }.commitNow()
        } else {
            searchModel.search("")
            showSection(notebooksSelected)
        }
        onPrepareOptionsMenu(binding.toolbar.menu)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_memos", false)) {
            searchItem?.collapseActionView()
            showSection(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notebooks_selected", notebooksSelected)
        outState.putBoolean("search_active", searchActive)
        super.onSaveInstanceState(outState)
    }

    /** Observe only the list area; leave vertical scrolling and all editor gestures native. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!::binding.isInitialized) return super.dispatchTouchEvent(event)
        if (searchActive) return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val area = Rect()
                binding.fragmentContainer.getGlobalVisibleRect(area)
                val buttons = IntArray(2).also(binding.bottomButtonBar::getLocationOnScreen)
                swipeCandidate = area.contains(event.rawX.toInt(), event.rawY.toInt()) && event.rawY < buttons[1]
                swipeConsumed = false
                swipeStartX = event.rawX
                swipeStartY = event.rawY
            }
            MotionEvent.ACTION_POINTER_DOWN -> swipeCandidate = false
            MotionEvent.ACTION_MOVE -> {
                if (swipeConsumed) return true
                if (swipeCandidate && event.pointerCount == 1) {
                    val dx = event.rawX - swipeStartX
                    val dy = event.rawY - swipeStartY
                    val threshold = 64 * resources.displayMetrics.density
                    if (abs(dy) > threshold / 2 && abs(dy) >= abs(dx)) swipeCandidate = false
                    val target = if (dx < 0) true else false
                    if (abs(dx) >= threshold && abs(dx) > abs(dy) * 1.5f &&
                        target != notebooksSelected && !supportFragmentManager.isStateSaved) {
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        swipeConsumed = true
                        swipeCandidate = false
                        showSection(target)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeCandidate = false
                if (swipeConsumed) { swipeConsumed = false; return true }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_speech_language)?.isVisible = !searchActive && !notebooksSelected
        listOf(R.id.action_create_backup, R.id.action_restore_backup, R.id.action_about).forEach {
            menu.findItem(it)?.isVisible = !searchActive
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val item = menu.findItem(R.id.action_search)
        searchItem = item
        val searchView = item.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(text: String?): Boolean {
                searchModel.search(text.orEmpty())
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean { showSearch(true); return true }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.clearFocus()
                showSearch(false)
                return true
            }
        })
        if (restoreSearch) {
            restoreSearch = false
            val query = searchModel.query.value
            item.expandActionView()
            searchView.setQuery(query, false)
            searchView.clearFocus()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val noteList = noteListFragment()
        when (item.itemId) {
            R.id.action_speech_language -> {
                val languages = SpeechLanguage.entries
                AlertDialog.Builder(this)
                    .setTitle(R.string.speech_language)
                    .setSingleChoiceItems(
                        arrayOf(getString(R.string.language_chinese), getString(R.string.language_english)),
                        languages.indexOf(SpeechPreferences.language(this))
                    ) { dialog, index ->
                        SpeechPreferences.setLanguage(this, languages[index])
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return true
            }
            R.id.action_create_backup -> {
                noteList?.openBackupCreator()
                return true
            }
            R.id.action_restore_backup -> {
                noteList?.openBackupPicker()
                return true
            }
            R.id.action_about -> {
                val versionName = packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName
                    .orEmpty()
                AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(getString(R.string.about_message, versionName))
                    .setPositiveButton(R.string.about_close, null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun noteListFragment(): NoteListFragment? = supportFragmentManager
        .findFragmentByTag("memos") as? NoteListFragment
}
