package com.woocommerce.android.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.ActivityUtils
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_help.*
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.SiteStore
import java.util.ArrayList
import javax.inject.Inject

class HelpActivity : AppCompatActivity() {
    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var siteStore: SiteStore
    @Inject lateinit var supportHelper: SupportHelper
    @Inject lateinit var zendeskHelper: ZendeskHelper
    @Inject lateinit var selectedSite: SelectedSite

    private val originFromExtras by lazy {
        (intent.extras?.get(HelpActivity.ORIGIN_KEY) as Origin?) ?: Origin.UNKNOWN
    }

    private val extraTagsFromExtras by lazy {
        intent.extras?.getStringArrayList(HelpActivity.EXTRA_TAGS_KEY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_help)

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        contactContainer.setOnClickListener { createNewZendeskTicket() }
        myTicketsContainer.setOnClickListener { showZendeskTickets() }
        faqContainer.setOnClickListener { showZendeskFaq() }

        /**
        * If the user taps on a Zendesk notification, we want to show them the `My Tickets` page. However, this
        * should only be triggered when the activity is first created, otherwise if the user comes back from
        * `My Tickets` and rotates the screen (or triggers the activity re-creation in any other way) it'll navigate
        * them to `My Tickets` again since the `originFromExtras` will still be [Origin.ZENDESK_NOTIFICATION].
         */
        if (savedInstanceState == null && originFromExtras == Origin.ZENDESK_NOTIFICATION) {
            showZendeskTickets()
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewZendeskTicket() {
        // if the user hasn't set their contact info yet prompt for it now
        if (!AppPrefs.hasSupportEmail()) {
            showIdentityDialog()
            return
        }

        zendeskHelper.createNewTicket(this, originFromExtras, selectedSite.get(), extraTagsFromExtras)
    }

    private fun showIdentityDialog() {
        val emailSuggestion = supportHelper
                .getSupportEmailAndNameSuggestion(accountStore.account, selectedSite.get()).first

        supportHelper.showSupportIdentityInputDialog(this, emailSuggestion, isNameInputHidden = true) { email, _ ->
            zendeskHelper.setSupportEmail(email)
            AnalyticsTracker.track(Stat.SUPPORT_IDENTITY_SET)
            createNewZendeskTicket()
        }
        AnalyticsTracker.track(Stat.SUPPORT_IDENTITY_FORM_VIEWED)
    }

    private fun showZendeskTickets() {
        zendeskHelper.showAllTickets(this, originFromExtras, selectedSite.get(), extraTagsFromExtras)
    }

    private fun showZendeskFaq() {
        ActivityUtils.openUrlExternal(this, FAQ_URL)
        /* TODO: for now we simply link to the online FAQ, but we should show the Zendesk FAQ once it's ready
        zendeskHelper
                .showZendeskHelpCenter(this, originFromExtras, selectedSite.get(), extraTagsFromExtras)
        */
    }

    enum class Origin(private val stringValue: String) {
        UNKNOWN("origin:unknown"),
        MAIN_ACTIVITY("origin:main-activity"),
        ZENDESK_NOTIFICATION("origin:zendesk-notification");

        override fun toString(): String {
            return stringValue
        }
    }

    companion object {
        private const val ORIGIN_KEY = "ORIGIN_KEY"
        private const val EXTRA_TAGS_KEY = "EXTRA_TAGS_KEY"
        private const val FAQ_URL = "https://docs.woocommerce.com/document/frequently-asked-questions/"

        @JvmStatic
        fun createIntent(
            context: Context,
            origin: Origin,
            extraSupportTags: List<String>?
        ): Intent {
            val intent = Intent(context, HelpActivity::class.java)
            intent.putExtra(HelpActivity.ORIGIN_KEY, origin)
            if (extraSupportTags != null && !extraSupportTags.isEmpty()) {
                intent.putStringArrayListExtra(HelpActivity.EXTRA_TAGS_KEY, extraSupportTags as ArrayList<String>?)
            }
            return intent
        }
    }
}
