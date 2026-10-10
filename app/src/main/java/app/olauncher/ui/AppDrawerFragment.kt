package app.olauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showToast

/**
 * "Select an app": the plain list used to choose the app behind a gesture, the date or the key.
 * Browsing and searching apps to open them happens on the home screen's sheet instead; this
 * screen only picks. Its search field takes the keyboard only when tapped.
 */
class AppDrawerFragment : Fragment() {

    private lateinit var adapter: AppDrawerAdapter
    private var flag = Constants.FLAG_SET_SWIPE_LEFT_APP

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        flag = arguments?.getInt(Constants.Key.FLAG, flag) ?: flag
        initViews()
        initAdapter()
        viewModel.appList.observe(viewLifecycleOwner) {
            adapter.setAppList(it.orEmpty().toMutableList())
        }
    }

    private fun initViews() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, ime.bottom))
            insets
        }
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Enter picks nothing: an app is chosen only by tapping it.
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.search.hideKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                adapter.search(newText)
                return true
            }
        })
        binding.search.findViewById<View>(androidx.appcompat.R.id.search_src_text)?.let {
            ViewCompat.setAccessibilityDelegate(it, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.hintText = getString(R.string.select_an_app)
                }
            })
        }
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            Prefs(requireContext()).appLabelAlignment,
            appClickListener = { appModel ->
                // A double tap lands here twice; only the first, while the picker is still
                // current, may act.
                if (!isPickerCurrent()) return@AppDrawerAdapter
                if (flag == Constants.FLAG_SET_PASSWORD_APP && appModel !is AppModel.App) {
                    // Only a launchable app can be the password manager; stay here to pick again.
                    requireContext().showToast(R.string.password_manager_needs_app)
                    return@AppDrawerAdapter
                }
                viewModel.selectedApp(appModel, flag)
                findNavController().popBackStack()
            },
            // The picker has no app menu.
            appInfoListener = {},
            appDeleteListener = {},
            appRenameListener = { _, _ -> },
            appCategoryListener = {},
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        // Scrolling the list puts the keyboard away so the list has the room.
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) binding.search.hideKeyboard()
            }
        })
    }

    private fun isPickerCurrent(): Boolean =
        findNavController().currentDestination?.id == R.id.appListFragment

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
