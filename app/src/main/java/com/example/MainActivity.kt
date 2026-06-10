package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Medicine
import com.example.data.MedicineBackupHelper
import com.example.ui.MedicineFilter
import com.example.ui.MedicineViewModel
import com.example.ui.theme.*
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel: MedicineViewModel by viewModels()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    PharmaCareApp(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmaCareApp(
    viewModel: MedicineViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val medicines by viewModel.filteredMedicines.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    // Counts mapping
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val expiredCount by viewModel.expiredCount.collectAsStateWithLifecycle()
    val nearExpiryCount by viewModel.nearExpiryCount.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()

    // Financial Analytics tracking
    val totalInvestment by viewModel.totalInvestment.collectAsStateWithLifecycle()
    val totalPotentialRevenue by viewModel.totalPotentialRevenue.collectAsStateWithLifecycle()

    // Dialog state controllers
    var showAddEditDialog by remember { mutableStateOf(false) }
    var medicineToEdit by remember { mutableStateOf<Medicine?>(null) }

    // Backup & Restore state controllers
    var showBackupDialog by remember { mutableStateOf(false) }
    var showConfirmImportDialog by remember { mutableStateOf(false) }
    var importedList by remember { mutableStateOf<List<Medicine>>(emptyList()) }

    var showDeveloperInfoDialog by remember { mutableStateOf(false) }

    // Multi-Select Billing Mode states
    var selectBillingModeActive by remember { mutableStateOf(false) }
    var selectedMedicineIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showCombinedQuantityDialog by remember { mutableStateOf(false) }

    val sharedPrefs = remember { context.getSharedPreferences("PharmaCarePrefs", Context.MODE_PRIVATE) }
    var shopAddress by remember {
        mutableStateOf(sharedPrefs.getString("shop_address", "Prabhat Singh Medicos, Near Civil Hospital, Prayagraj, UP, India") ?: "Prabhat Singh Medicos, Near Civil Hospital, Prayagraj, UP, India")
    }
    var showEditAddressDialog by remember { mutableStateOf(false) }

    var shopName by remember {
        mutableStateOf(sharedPrefs.getString("shop_name", "Pharma Care") ?: "Pharma Care")
    }
    var operatorName by remember {
        mutableStateOf(sharedPrefs.getString("operator_name", "Prabhat Singh") ?: "Prabhat Singh")
    }
    var operatorPhone by remember {
        mutableStateOf(sharedPrefs.getString("operator_phone", "+91 9696971627") ?: "+91 9696971627")
    }
    var licenceNumber by remember {
        mutableStateOf(sharedPrefs.getString("licence_number", "DL-101/A-HOSP/2026") ?: "DL-101/A-HOSP/2026")
    }
    var showBillConfigDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Quick Toast notifications
    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // Storage Access Framework activity result launchers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val json = viewModel.getExportJson(context)
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                showToast("Backup file exported successfully!")
            } catch (e: Exception) {
                showToast("Failed to save backup file: ${e.message}")
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { r -> r.readText() }
                    try {
                        val imported = MedicineBackupHelper.importFromJsonString(context, json)
                        if (imported.isEmpty()) {
                            showToast("Backup contains no medicine entries.")
                        } else {
                            importedList = imported
                            showConfirmImportDialog = true
                        }
                    } catch (ex: Exception) {
                        showToast("Invalid JSON backup structure.")
                    }
                }
            } catch (e: Exception) {
                showToast("Error opening backup source: ${e.message}")
            }
        }
    }

    fun handlePasteImport() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                val imported = MedicineBackupHelper.importFromJsonString(context, text)
                if (imported.isEmpty()) {
                    showToast("No valid medicines backup found in clipboard.")
                } else {
                    importedList = imported
                    showConfirmImportDialog = true
                }
            } else {
                showToast("Clipboard is empty.")
            }
        } catch (e: Exception) {
            showToast("Invalid clipboard JSON database structure.")
        }
    }

    fun handleCopyExport() {
        try {
            val json = viewModel.getExportJson(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PharmaCareBackup", json)
            clipboard.setPrimaryClip(clip)
            showToast("Backup JSON code copied to clipboard!")
        } catch (e: Exception) {
            showToast("Failed to export: ${e.message}")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(320.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    // Header inside drawer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MedicalTeal.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = "Drawer Icon",
                                tint = MedicalTeal,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = shopName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedicalTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Billing Configurations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CURRENT BILL METADATA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Column {
                                Text("Store Name", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(shopName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                Text("Store Operator", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(operatorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                Text("Operator Phone", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(operatorPhone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                Text("Drug Licence No.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(licenceNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Edit Receipt", tint = MedicalTeal) },
                        label = { Text("Edit Bill Settings", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showBillConfigDialog = true
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MedicalTeal
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Place, contentDescription = "Edit Address", tint = MedicalTeal) },
                        label = { Text("Edit Shop Address", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showEditAddressDialog = true
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MedicalTeal
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "PharmaCare Suite v1.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Sleek Medical Branding Hero Header
                MedicalHeader(
                    shopName = shopName,
                    operatorName = operatorName,
                    shopAddress = shopAddress,
                    onMenuClick = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onEditAddressClick = { showEditAddressDialog = true },
                    onCallClick = { showDeveloperInfoDialog = true },
                    onBackupClick = { showBackupDialog = true }
                )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 2. Financial & Count Stats Horizontal Panels
                StatsSection(
                    totalCount = totalCount,
                    expiredCount = expiredCount,
                    nearExpiryCount = nearExpiryCount,
                    lowStockCount = lowStockCount,
                    totalInvestment = totalInvestment,
                    totalRevenue = totalPotentialRevenue
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Modern Real-Time Search & Categorization Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search meds by name or batch...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search Icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear text")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Quick Filter Chips Bar
                FilterChipsBar(
                    selectedFilter = selectedFilter,
                    onFilterChange = { viewModel.setFilter(it) },
                    totalCount = totalCount,
                    expiredCount = expiredCount,
                    nearExpiryCount = nearExpiryCount,
                    lowStockCount = lowStockCount
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Medicine Inventory List View
                if (medicines.isEmpty()) {
                    EmptyStateView(
                        queryActive = searchQuery.isNotEmpty(),
                        filterActive = selectedFilter != MedicineFilter.ALL,
                        onClearFilters = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilter(MedicineFilter.ALL)
                        },
                        onAddClick = {
                            medicineToEdit = null
                            showAddEditDialog = true
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectBillingModeActive) "Select Meds (${selectedMedicineIds.size} Selected)" else "Medicine Stock (${medicines.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = {
                                selectBillingModeActive = !selectBillingModeActive
                                if (!selectBillingModeActive) {
                                    selectedMedicineIds = emptySet()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selectBillingModeActive) MedicalCrimson else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("multi_bill_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (selectBillingModeActive) Icons.Default.Close else Icons.Default.ReceiptLong,
                                contentDescription = if (selectBillingModeActive) "Cancel selection" else "Choose medicines to bill",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectBillingModeActive) "Cancel Selection" else "Create Combined Bill",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = if (selectBillingModeActive) 110.dp else 88.dp), // Clear space for FAB or Action Bar
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("medicine_list")
                    ) {
                        items(
                            items = medicines,
                            key = { it.id }
                        ) { medicine ->
                            val isSelected = selectedMedicineIds.contains(medicine.id)
                            MedicineStockCard(
                                medicine = medicine,
                                currentTimestamp = System.currentTimeMillis(),
                                onEditClick = {
                                    medicineToEdit = medicine
                                    showAddEditDialog = true
                                },
                                onDeleteClick = {
                                    viewModel.deleteMedicine(medicine)
                                    showToast("${medicine.name} deleted successfully")
                                },
                                onPrintClick = {
                                    com.example.util.PdfPrintHelper.printMedicineBill(
                                        context,
                                        medicine,
                                        shopName,
                                        operatorName,
                                        operatorPhone,
                                        licenceNumber,
                                        shopAddress
                                    )
                                },
                                selectBillingModeActive = selectBillingModeActive,
                                isSelected = isSelected,
                                onSelectedChange = { checked ->
                                    selectedMedicineIds = if (checked) {
                                        selectedMedicineIds + medicine.id
                                    } else {
                                        selectedMedicineIds - medicine.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button to launch Add form (hidden when in billing mode)
        if (!selectBillingModeActive) {
            FloatingActionButton(
                onClick = {
                    medicineToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_medicine_fab")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Medicine")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Med", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Floating Combined Invoice / Receipt Action Bar
        if (selectBillingModeActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .testTag("multi_bill_action_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedMedicineIds.size} Item(s) Selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Generate a combined invoice bill",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { selectedMedicineIds = emptySet() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                if (selectedMedicineIds.isEmpty()) {
                                    showToast("Please select at least 1 medicine to print bill")
                                } else {
                                    showCombinedQuantityDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("generate_combined_bill_btn")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Bill", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // Modal Edit/Add Dialog sheet
    if (showAddEditDialog) {
        AddEditMedicineDialog(
            medicine = medicineToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, mrp, buy, sell, expiry, stock, batch, photoUri ->
                if (medicineToEdit == null) {
                    viewModel.addMedicine(name, mrp, buy, sell, expiry, stock, batch, photoUri)
                    showToast("New medicine registered")
                } else {
                    viewModel.updateMedicine(medicineToEdit!!.id, name, mrp, buy, sell, expiry, stock, batch, photoUri)
                    showToast("Medicine item updated")
                }
                showAddEditDialog = false
            }
        )
    }

    // Offline Backup & Restore Dialog
    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { showBackupDialog = false },
            onExportClipboard = {
                handleCopyExport()
                showBackupDialog = false
            },
            onExportFile = {
                try {
                    createDocumentLauncher.launch("pharma_care_backup.json")
                } catch (e: Exception) {
                    showToast("Could not launch file saver: ${e.message}")
                }
                showBackupDialog = false
            },
            onExportPdf = {
                if (medicines.isEmpty()) {
                    showToast("Database is empty. Nothing to export to PDF.")
                } else {
                    com.example.util.PdfPrintHelper.printInventoryBackupPdf(
                        context,
                        medicines,
                        shopName,
                        operatorName,
                        operatorPhone,
                        licenceNumber,
                        shopAddress
                    )
                }
                showBackupDialog = false
            },
            onImportClipboard = {
                handlePasteImport()
                showBackupDialog = false
            },
            onImportFile = {
                try {
                    openDocumentLauncher.launch(arrayOf("application/json"))
                } catch (e: Exception) {
                    showToast("Could not launch file selector: ${e.message}")
                }
                showBackupDialog = false
            }
        )
    }

    // Separated Developer Support & Info Dialog
    if (showDeveloperInfoDialog) {
        DeveloperSupportDialog(
            developerName = "Prabhat Singh",
            contactNumber = "9696971627",
            onDismiss = { showDeveloperInfoDialog = false },
            onCallClick = {
                dialPhoneNumber(context, "9696971627")
                showDeveloperInfoDialog = false
            }
        )
    }

    // Editable Shop Address Dialog
    if (showEditAddressDialog) {
        var tempAddress by remember { mutableStateOf(shopAddress) }
        AlertDialog(
            onDismissRequest = { showEditAddressDialog = false },
            title = {
                Text(
                    text = "Update Shop Address",
                    fontWeight = FontWeight.Bold,
                    color = MedicalTeal,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "This address will be printed at the top center/metadata panel of all generated invoices and bills.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempAddress,
                        onValueChange = { tempAddress = it },
                        label = { Text("Shop Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalTeal,
                            focusedLabelColor = MedicalTeal
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempAddress.trim()
                        if (trimmed.isNotEmpty()) {
                            shopAddress = trimmed
                            sharedPrefs.edit().putString("shop_address", trimmed).apply()
                            showToast("Shop address updated successfully!")
                        }
                        showEditAddressDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                ) {
                    Text("Save Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAddressDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Confirm Merge/Replace Dialog
    if (showConfirmImportDialog) {
        ConfirmImportDialog(
            importedList = importedList,
            onDismiss = {
                showConfirmImportDialog = false
                importedList = emptyList()
            },
            onConfirm = { replaceExisting ->
                viewModel.batchImport(importedList, replaceExisting) { count, error ->
                    if (error == null) {
                        showToast("Successfully imported $count medicines!")
                    } else {
                        showToast("Import failed: $error")
                    }
                }
                showConfirmImportDialog = false
                importedList = emptyList()
            }
        )
    }

    // Modal Combined Multi-Item Quantity Configuration Dialog
    if (showCombinedQuantityDialog) {
        val selectedMedicines = medicines.filter { selectedMedicineIds.contains(it.id) }
        CombinedQuantityDialog(
            selectedMedicines = selectedMedicines,
            onDismiss = { showCombinedQuantityDialog = false },
            onConfirm = { quantitiesMap ->
                com.example.util.PdfPrintHelper.printMultiMedicineBill(
                    context,
                    selectedMedicines,
                    quantitiesMap,
                    shopName,
                    operatorName,
                    operatorPhone,
                    licenceNumber,
                    shopAddress
                )
                showCombinedQuantityDialog = false
                selectedMedicineIds = emptySet()
                selectBillingModeActive = false
            }
        )
    }

    // Editable Bill Settings Dialog
    if (showBillConfigDialog) {
        var tempShopName by remember { mutableStateOf(shopName) }
        var tempOperatorName by remember { mutableStateOf(operatorName) }
        var tempOperatorPhone by remember { mutableStateOf(operatorPhone) }
        var tempLicenceNumber by remember { mutableStateOf(licenceNumber) }

        AlertDialog(
            onDismissRequest = { showBillConfigDialog = false },
            title = {
                Text(
                    text = "Edit Bill Settings",
                    fontWeight = FontWeight.Bold,
                    color = MedicalTeal,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Update medical store billing details and license numbers dynamically. This information is printed directly onto receipts and backup records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = tempShopName,
                        onValueChange = { tempShopName = it },
                        label = { Text("Medical Store Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalTeal,
                            focusedLabelColor = MedicalTeal
                        )
                    )
                    OutlinedTextField(
                        value = tempOperatorName,
                        onValueChange = { tempOperatorName = it },
                        label = { Text("Store Operator / Owner") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalTeal,
                            focusedLabelColor = MedicalTeal
                        )
                    )
                    OutlinedTextField(
                        value = tempOperatorPhone,
                        onValueChange = { tempOperatorPhone = it },
                        label = { Text("Operator Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalTeal,
                            focusedLabelColor = MedicalTeal
                        )
                    )
                    OutlinedTextField(
                        value = tempLicenceNumber,
                        onValueChange = { tempLicenceNumber = it },
                        label = { Text("Medical License/DL Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalTeal,
                            focusedLabelColor = MedicalTeal
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sn = tempShopName.trim()
                        val opn = tempOperatorName.trim()
                        val opp = tempOperatorPhone.trim()
                        val lic = tempLicenceNumber.trim()

                        if (sn.isNotEmpty() && opn.isNotEmpty() && opp.isNotEmpty()) {
                            shopName = sn
                            operatorName = opn
                            operatorPhone = opp
                            licenceNumber = lic

                            sharedPrefs.edit()
                                .putString("shop_name", sn)
                                .putString("operator_name", opn)
                                .putString("operator_phone", opp)
                                .putString("licence_number", lic)
                                .apply()

                            showToast("Bill settings updated successfully!")
                            showBillConfigDialog = false
                        } else {
                            showToast("Store name, operator name and phone cannot be empty!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBillConfigDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
}

// 1. Branding Header with Developer details + dial action + backup action
@Composable
fun MedicalHeader(
    shopName: String,
    operatorName: String,
    shopAddress: String,
    onMenuClick: () -> Unit,
    onEditAddressClick: () -> Unit,
    onCallClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MedicalTeal, MedicalMint)
                )
            )
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hamburger three-line button
            IconButton(
                onClick = onMenuClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("hamburger_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu Options Drawer",
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Medical Store Logo Setup
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = "Medical Store Logo",
                    tint = MedicalTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = operatorName.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onEditAddressClick() }
                        .padding(vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Edit address",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = shopAddress,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit indicator",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // High-polish backup button
            IconButton(
                onClick = onBackupClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("backup_db_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsBackupRestore,
                    contentDescription = "Database Backup",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // High-polish Developer Info / Support Option button (Separated)
            IconButton(
                onClick = onCallClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("contact_dial_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Contact Support / Developer Info",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 2. Statistics Section
@Composable
fun StatsSection(
    totalCount: Int,
    expiredCount: Int,
    nearExpiryCount: Int,
    lowStockCount: Int,
    totalInvestment: Double,
    totalRevenue: Double
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Investment Valuation summary bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL INVESTED",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.2f", totalInvestment)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MedicalTeal
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(32.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EST. REVENUE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.2f", totalRevenue)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MedicalMint
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid-like horizontal details row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniStatsCard(
                title = "Total Medicines",
                value = totalCount.toString(),
                systemIcon = Icons.Default.Inventory,
                badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                textColor = MaterialTheme.colorScheme.primary
            )

            MiniStatsCard(
                title = "Expired Items",
                value = expiredCount.toString(),
                systemIcon = Icons.Default.Cancel,
                badgeColor = MedicalCrimson.copy(alpha = 0.15f),
                textColor = MedicalCrimson
            )

            MiniStatsCard(
                title = "Near Expiry Alerts",
                value = nearExpiryCount.toString(),
                systemIcon = Icons.Default.AssignmentLate,
                badgeColor = MedicalGold.copy(alpha = 0.15f),
                textColor = MedicalGold
            )

            MiniStatsCard(
                title = "Low Stock Alerts",
                value = lowStockCount.toString(),
                systemIcon = Icons.Default.ErrorOutline,
                badgeColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                textColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun MiniStatsCard(
    title: String,
    value: String,
    systemIcon: ImageVector,
    badgeColor: Color,
    textColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(140.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(badgeColor, CircleShape)
                ) {
                    Icon(
                        imageVector = systemIcon,
                        contentDescription = title,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 4. Quick Filter Chips Bar
@Composable
fun FilterChipsBar(
    selectedFilter: MedicineFilter,
    onFilterChange: (MedicineFilter) -> Unit,
    totalCount: Int,
    expiredCount: Int,
    nearExpiryCount: Int,
    lowStockCount: Int
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterBadgeChip(
            label = "All Stocks",
            count = totalCount,
            isSelected = selectedFilter == MedicineFilter.ALL,
            onClick = { onFilterChange(MedicineFilter.ALL) }
        )

        FilterBadgeChip(
            label = "Active Only",
            count = totalCount - expiredCount,
            isSelected = selectedFilter == MedicineFilter.ACTIVE,
            onClick = { onFilterChange(MedicineFilter.ACTIVE) }
        )

        FilterBadgeChip(
            label = "Near Expiry Alert",
            count = nearExpiryCount,
            isSelected = selectedFilter == MedicineFilter.NEAR_EXPIRY,
            onClick = { onFilterChange(MedicineFilter.NEAR_EXPIRY) },
            highlightColor = MedicalGold
        )

        FilterBadgeChip(
            label = "Expired Alerts",
            count = expiredCount,
            isSelected = selectedFilter == MedicineFilter.EXPIRED,
            onClick = { onFilterChange(MedicineFilter.EXPIRED) },
            highlightColor = MedicalCrimson
        )

        FilterBadgeChip(
            label = "Low Stock (<5)",
            count = lowStockCount,
            isSelected = selectedFilter == MedicineFilter.LOW_STOCK,
            onClick = { onFilterChange(MedicineFilter.LOW_STOCK) }
        )
    }
}

@Composable
fun FilterBadgeChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    highlightColor: Color? = null
) {
    val bgColor = if (isSelected) {
        highlightColor ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isSelected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val chipBorderColor = if (isSelected) {
        Color.Transparent
    } else if (highlightColor != null && count > 0) {
        highlightColor.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, chipBorderColor, RoundedCornerShape(16.dp)),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 5. Medicine Stock listcard
@Composable
fun MedicineStockCard(
    medicine: Medicine,
    currentTimestamp: Long,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPrintClick: () -> Unit,
    selectBillingModeActive: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    val isExpired = medicine.expiryTimestamp < currentTimestamp
    val thirtyDaysFromNow = currentTimestamp + 30L * 24 * 60 * 60 * 1000
    val isNearExpiry = medicine.expiryTimestamp in currentTimestamp..thirtyDaysFromNow

    // State outline indicator based on warning
    val cardBorderColor = when {
        isExpired -> MedicalCrimson
        isNearExpiry -> MedicalGold
        medicine.stockQty <= 5 -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val cardBorderWidth = if (cardBorderColor != Color.Transparent) 1.5.dp else 1.dp
    val outlineDrawColor = if (cardBorderColor != Color.Transparent) cardBorderColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(cardBorderWidth, outlineDrawColor, RoundedCornerShape(16.dp))
            .then(
                if (selectBillingModeActive) {
                    Modifier.clickable { onSelectedChange(!isSelected) }
                } else Modifier
            )
            .testTag("medicine_card_${medicine.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Badges alert header row
            if (isExpired || isNearExpiry || medicine.stockQty <= 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isExpired) {
                        BadgeLabel(
                            text = "EXPIRED PRODUCT",
                            backgroundColor = MedicalCrimson,
                            icon = Icons.Default.Cancel
                        )
                    } else if (isNearExpiry) {
                        BadgeLabel(
                            text = "NEAR EXPIRY ALERT",
                            backgroundColor = MedicalGold,
                            icon = Icons.Default.Warning
                        )
                    }

                    if (medicine.stockQty <= 5) {
                        BadgeLabel(
                            text = if (medicine.stockQty == 0) "OUT OF STOCK" else "LOW STOCK ALERT",
                            backgroundColor = MaterialTheme.colorScheme.secondary,
                            icon = Icons.Default.ReportProblem
                        )
                    }
                }
            }

            // Medicine Name and Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox for selection mode
                if (selectBillingModeActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectedChange,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("med_select_${medicine.id}")
                    )
                }

                // Item Photo/Icon Thumbnail
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val presets = listOf(
                        Triple("med_icon_tablet", Icons.Default.Medication, Color(0xFFE3F2FD)),
                        Triple("med_icon_vaccines", Icons.Default.Vaccines, Color(0xFFFFF1F1)),
                        Triple("med_icon_healing", Icons.Default.Healing, Color(0xFFE8F5E9)),
                        Triple("med_icon_spa", Icons.Default.Spa, Color(0xFFE0F2F1)),
                        Triple("med_icon_liquid", Icons.Default.WaterDrop, Color(0xFFFFF3E0)),
                        Triple("med_icon_emergency", Icons.Default.Emergency, Color(0xFFFFFDE7)),
                        Triple("med_icon_science", Icons.Default.Science, Color(0xFFF3E5F5)),
                        Triple("med_icon_thermostat", Icons.Default.Thermostat, Color(0xFFECEFF1))
                    )

                    val isPreset = presets.any { it.first == medicine.photoUri }
                    if (medicine.photoUri.isNotEmpty() && !isPreset) {
                        AsyncImage(
                            model = medicine.photoUri,
                            contentDescription = "Medicine image thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        val matching = presets.find { it.first == medicine.photoUri }
                        if (matching != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(matching.third),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = matching.second,
                                    contentDescription = "Preset icon",
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = "Default medicine icon",
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Batch: ${medicine.batchNumber.ifEmpty { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Edit, Delete & Print standard icons buttons
                if (!selectBillingModeActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPrintClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("print_med_${medicine.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print medicine details invoice",
                                tint = MedicalGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("edit_med_${medicine.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Medicine State",
                                tint = MedicalTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delete_med_${medicine.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Remove Medicine State",
                                tint = MedicalCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pricing details grids
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceItem(label = "Buy Cost", value = medicine.buyPrice, tint = MaterialTheme.colorScheme.onBackground)
                PriceItem(label = "Sell Rate", value = medicine.sellPrice, tint = MedicalTeal, isAccent = true)
                PriceItem(label = "MRP Max", value = medicine.mrp, tint = MedicalGold)
                
                // Inventory quantities pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Stock Level",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (medicine.stockQty <= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MedicalTeal.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${medicine.stockQty} Pcs",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (medicine.stockQty <= 5) MedicalCrimson else MedicalTeal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expiry calendar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Expiry Date",
                        tint = if (isExpired) MedicalCrimson else if (isNearExpiry) MedicalGold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Expires: ${formatExpiryDate(medicine.expiryTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isExpired) MedicalCrimson else if (isNearExpiry) MedicalGold else MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Remaining timeline calculation text
                val timelineText = when {
                    isExpired -> {
                        val passedDays = ((currentTimestamp - medicine.expiryTimestamp) / (24L * 60 * 60 * 1000)).toInt()
                        "Expired $passedDays days ago"
                    }
                    else -> {
                        val remainingDays = ((medicine.expiryTimestamp - currentTimestamp) / (24L * 60 * 60 * 1000)).toInt()
                        if (remainingDays <= 0) "Expiring Today" else "Expires in $remainingDays days"
                    }
                }

                Text(
                    text = timelineText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired) MedicalCrimson else if (isNearExpiry) MedicalGold else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PriceItem(
    label: String,
    value: Double,
    tint: Color,
    isAccent: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "₹${String.format(Locale.getDefault(), "%.2f", value)}",
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontWeight = if (isAccent) FontWeight.Black else FontWeight.Bold
        )
    }
}

@Composable
fun BadgeLabel(
    text: String,
    backgroundColor: Color,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .background(backgroundColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(1.dp, backgroundColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = backgroundColor,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = backgroundColor,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp
            )
        }
    }
}

// Dialog helper editors
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicineDialog(
    medicine: Medicine?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        mrp: Double,
        buy: Double,
        sell: Double,
        expiry: Long,
        stock: Int,
        batch: String,
        photoUri: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(medicine?.name ?: "") }
    var batch by remember { mutableStateOf(medicine?.batchNumber ?: "") }
    var mrpStr by remember { mutableStateOf(medicine?.mrp?.toString() ?: "") }
    var buyPriceStr by remember { mutableStateOf(medicine?.buyPrice?.toString() ?: "") }
    var sellPriceStr by remember { mutableStateOf(medicine?.sellPrice?.toString() ?: "") }
    var stockQtyStr by remember { mutableStateOf(medicine?.stockQty?.toString() ?: "10") }
    var photoUri by remember { mutableStateOf(medicine?.photoUri ?: "med_icon_tablet") }
    
    // Choose Expiry - either preselected or 6 months out by default
    val calendar = Calendar.getInstance()
    if (medicine == null) {
        calendar.add(Calendar.MONTH, 6)
    } else {
        calendar.timeInMillis = medicine.expiryTimestamp
    }
    var expiryTimestamp by remember { mutableStateOf(calendar.timeInMillis) }

    val context = LocalContext.current

    // Image Picker Launcher setup
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val directory = java.io.File(context.filesDir, "medicine_images")
                    if (!directory.exists()) {
                        directory.mkdirs()
                    }
                    val fileName = "img_${System.currentTimeMillis()}.png"
                    val file = java.io.File(directory, fileName)
                    file.outputStream().use { outputStream ->
                        inputStream.use { it.copyTo(outputStream) }
                    }
                    file.absolutePath
                } else {
                    uri.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uri.toString()
            }
            photoUri = savedPath
        }
    }

    // List of gorgeous preloaded clinic visual categories references
    data class PresetIcon(val id: String, val name: String, val icon: ImageVector, val color: Color)
    val presets = listOf(
        PresetIcon("med_icon_tablet", "Tablet", Icons.Default.Medication, Color(0xFFE3F2FD)),
        PresetIcon("med_icon_vaccines", "Inject", Icons.Default.Vaccines, Color(0xFFFFF1F1)),
        PresetIcon("med_icon_healing", "Ointment", Icons.Default.Healing, Color(0xFFE8F5E9)),
        PresetIcon("med_icon_spa", "Herbs", Icons.Default.Spa, Color(0xFFE0F2F1)),
        PresetIcon("med_icon_liquid", "Liquid", Icons.Default.WaterDrop, Color(0xFFFFF3E0)),
        PresetIcon("med_icon_emergency", "First-aid", Icons.Default.Emergency, Color(0xFFFFFDE7)),
        PresetIcon("med_icon_science", "Syringe", Icons.Default.Science, Color(0xFFF3E5F5)),
        PresetIcon("med_icon_thermostat", "Device", Icons.Default.Thermostat, Color(0xFFECEFF1))
    )

    // Validation triggers
    var nameError by remember { mutableStateOf(false) }
    var mrpError by remember { mutableStateOf(false) }
    var buyError by remember { mutableStateOf(false) }
    var sellError by remember { mutableStateOf(false) }
    var stockError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title and Icon header row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MedicalTeal.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (medicine == null) Icons.Default.AddHomeWork else Icons.Default.Edit,
                            contentDescription = null,
                            tint = MedicalTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (medicine == null) "Add New Medicine" else "Modify Stock Item",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PHOTO & PRESET CATEGORY SELECTOR VIEW
                Text(
                    text = "ITEM PICTURE / SPECIAL ICON",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MedicalTeal,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    // Circle preview of current state
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .border(
                                2.dp,
                                MedicalTeal,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { photoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val isPreset = presets.any { it.id == photoUri }
                        if (photoUri.isNotEmpty() && !isPreset) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Custom image logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            val activePreset = presets.find { it.id == photoUri }
                            if (activePreset != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(activePreset.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = activePreset.icon,
                                        contentDescription = activePreset.name,
                                        tint = MedicalTeal,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = "Pill icon indicator",
                                    tint = MedicalTeal,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Custom Gallery item option
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { photoLauncher.launch("image/*") }
                                    .padding(vertical = 2.dp)
                             ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MedicalTeal.copy(alpha = 0.12f), CircleShape)
                                        .border(
                                            1.dp,
                                            if (photoUri.isNotEmpty() && !presets.any { it.id == photoUri }) MedicalTeal else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Upload custom phone photogrid file",
                                        tint = MedicalTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Upload", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }

                            // Preset categories
                            presets.forEach { pr ->
                                val isSelected = photoUri == pr.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { photoUri = pr.id }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(pr.color, CircleShape)
                                            .border(
                                                2.dp,
                                                if (isSelected) MedicalTeal else Color.Transparent,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = pr.icon,
                                            contentDescription = pr.name,
                                            tint = if (isSelected) MedicalTeal else Color.DarkGray.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(pr.name, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isEmpty()
                    },
                    label = { Text("Medicine Name *") },
                    placeholder = { Text("Paracetamol, Ibuprofen...") },
                    leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null, tint = MedicalTeal) },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Name cannot be empty", color = MedicalCrimson) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_med_name")
                )

                // Batch code field
                OutlinedTextField(
                    value = batch,
                    onValueChange = { batch = it },
                    label = { Text("Batch Number") },
                    placeholder = { Text("E.g., B7042") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_batch")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Side-by-side Pricing Grid Cost vs Sell Rate
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Buy Cost
                    OutlinedTextField(
                        value = buyPriceStr,
                        onValueChange = {
                            buyPriceStr = it
                            buyError = it.toDoubleOrNull() == null || it.toDouble() < 0
                        },
                        label = { Text("Buy Price *") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = buyError,
                        supportingText = { if (buyError) Text("Invalid buy rate") else null },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_buy_price")
                    )

                    // Sell Rate
                    OutlinedTextField(
                        value = sellPriceStr,
                        onValueChange = {
                            sellPriceStr = it
                            sellError = it.toDoubleOrNull() == null || it.toDouble() < 0
                        },
                        label = { Text("Sell Price *") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = sellError,
                        supportingText = { if (sellError) Text("Invalid sell rate") else null },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_sell_price")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // MRP and Stocks row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // MRP Max limits
                    OutlinedTextField(
                        value = mrpStr,
                        onValueChange = {
                            mrpStr = it
                            mrpError = it.toDoubleOrNull() == null || it.toDouble() < 0
                        },
                        label = { Text("Max MRP *") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = mrpError,
                        supportingText = { if (mrpError) Text("Invalid MRP") else null },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_mrp")
                    )

                    // Quantities levels
                    OutlinedTextField(
                        value = stockQtyStr,
                        onValueChange = {
                            stockQtyStr = it
                            stockError = it.toIntOrNull() == null || it.toInt() < 0
                        },
                        label = { Text("Stock Qty *") },
                        placeholder = { Text("10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = stockError,
                        supportingText = { if (stockError) Text("Invalid stock level") else null },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_stock")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expiry Picker Box Area
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable {
                            showDatePickerDialog(context, expiryTimestamp) { selected ->
                                expiryTimestamp = selected
                            }
                        }
                        .fillMaxWidth()
                        .testTag("expiry_date_picker_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Expiry date calendar",
                                tint = MedicalTeal
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MEDICINE EXPIRY DATE *",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatExpiryDate(expiryTimestamp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = "Edit Expiry Calendar",
                            tint = MedicalTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions Cancel/Save Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val isNameValid = name.isNotBlank()
                            val buyVal = buyPriceStr.toDoubleOrNull()
                            val sellVal = sellPriceStr.toDoubleOrNull()
                            val mrpVal = mrpStr.toDoubleOrNull()
                            val stockVal = stockQtyStr.toIntOrNull()

                            nameError = !isNameValid
                            buyError = buyVal == null || buyVal < 0
                            sellError = sellVal == null || sellVal < 0
                            mrpError = mrpVal == null || mrpVal < 0
                            stockError = stockVal == null || stockVal < 0

                            if (isNameValid && !buyError && !sellError && !mrpError && !stockError) {
                                onSave(
                                    name,
                                    mrpVal!!,
                                    buyVal!!,
                                    sellVal!!,
                                    expiryTimestamp,
                                    stockVal!!,
                                    batch,
                                    photoUri
                                )
                            } else {
                                Toast.makeText(context, "Please correct the invalid fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("save_medicine_btn")
                    ) {
                        Text("Save Medicine", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// 6. Visual Blank Screens for No Data
@Composable
fun EmptyStateView(
    queryActive: Boolean,
    filterActive: Boolean,
    onClearFilters: () -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_view"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .background(MedicalTeal.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                imageVector = if (queryActive || filterActive) Icons.Default.FilterListOff else Icons.Default.Vaccines,
                contentDescription = "Empty stock logo",
                tint = MedicalTeal,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (queryActive || filterActive) "No Matching Medicines Found" else "Your Medicine Catalog is Empty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (queryActive || filterActive) {
                "Try readjusting your search term or active category filters to expand the match guidelines."
            } else {
                "Registered pharmacological products, buy price, sell price, MRP charts, and near-expiry statuses appear here. Get started by adding your first medicine!"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (queryActive || filterActive) {
            Button(
                onClick = onClearFilters,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Clear Searches & Filters", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add First Medicine", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Global Core helpers
private fun formatExpiryDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun showDatePickerDialog(
    context: Context,
    initialTimestamp: Long,
    onDateSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = initialTimestamp
    
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(Calendar.YEAR, year)
            selectedCalendar.set(Calendar.MONTH, month)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            selectedCalendar.set(Calendar.HOUR_OF_DAY, 23)
            selectedCalendar.set(Calendar.MINUTE, 59)
            selectedCalendar.set(Calendar.SECOND, 59)
            onDateSelected(selectedCalendar.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun dialPhoneNumber(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open dialer context", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupDialog(
    onDismiss: () -> Unit,
    onExportClipboard: () -> Unit,
    onExportFile: () -> Unit,
    onExportPdf: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MedicalTeal.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = "Backup icon",
                            tint = MedicalTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Backup & Recovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EXPORT SECTION
                Text(
                    text = "OFFLINE EXPORT (BACKUP)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MedicalTeal
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExportClipboard,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_clipboard_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy JSON Backup Code", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExportFile,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalTeal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_file_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as JSON Backup File", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExportPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_pdf_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export PDF Inventory Backup", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // IMPORT SECTION
                Text(
                    text = "OFFLINE IMPORT (RESTORE)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MedicalGold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onImportClipboard,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_clipboard_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paste from Clipboard", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onImportFile,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_file_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose JSON Backup File", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close Panel", fontWeight = FontWeight.Bold, color = MedicalTeal)
                }
            }
        }
    }
}

@Composable
fun ConfirmImportDialog(
    importedList: List<Medicine>,
    onDismiss: () -> Unit,
    onConfirm: (replaceExisting: Boolean) -> Unit
) {
    var replaceExisting by remember { mutableStateOf(false) }
    val importCount = importedList.size

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MedicalGold.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MedicalGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Confirm Data Import",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "A valid list of $importCount medicine items has been loaded. Choose how you would like to apply this backup:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Items in Backup Preview:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable preview section showing name, batch code, stock and image icons!
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(importedList) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            // Image/Preset icon
                            val dialogPresets = listOf(
                                Triple("med_icon_tablet", Icons.Default.Medication, Color(0xFFE3F2FD)),
                                Triple("med_icon_vaccines", Icons.Default.Vaccines, Color(0xFFFFF1F1)),
                                Triple("med_icon_healing", Icons.Default.Healing, Color(0xFFE8F5E9)),
                                Triple("med_icon_spa", Icons.Default.Spa, Color(0xFFE0F2F1)),
                                Triple("med_icon_liquid", Icons.Default.WaterDrop, Color(0xFFFFF3E0)),
                                Triple("med_icon_emergency", Icons.Default.Emergency, Color(0xFFFFFDE7)),
                                Triple("med_icon_science", Icons.Default.Science, Color(0xFFF3E5F5)),
                                Triple("med_icon_thermostat", Icons.Default.Thermostat, Color(0xFFECEFF1))
                            )
                            val presetMatch = dialogPresets.find { it.first == item.photoUri }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(presetMatch?.third ?: Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
                            ) {
                                val isPreset = dialogPresets.any { it.first == item.photoUri }
                                if (item.photoUri.isNotEmpty() && !isPreset) {
                                    AsyncImage(
                                        model = item.photoUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = presetMatch?.second ?: Icons.Default.Medication,
                                        contentDescription = null,
                                        tint = MedicalTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Batch: ${item.batchNumber.ifEmpty { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text(
                                    text = "Qty: ${item.stockQty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "₹${item.sellPrice}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MedicalTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selection of import mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceExisting = false }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = !replaceExisting,
                        onClick = { replaceExisting = false }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Merge with current list", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Adds imported items, keeping existing ones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceExisting = true }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = replaceExisting,
                        onClick = { replaceExisting = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Replace active inventory", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MedicalCrimson)
                        Text("Clears current items first, then loads backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onConfirm(replaceExisting) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (replaceExisting) MedicalCrimson else MedicalTeal
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Database", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginDialog(
    isAuthenticating: Boolean,
    onDismiss: () -> Unit,
    onRequestOtp: (String, (Boolean, String?) -> Unit) -> Unit,
    onVerifyCode: (String, String, (Boolean, String?) -> Unit) -> Unit,
    showToast: (String) -> Unit
) {
    var phoneOrUser by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF26A5E4).copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color(0xFF26A5E4),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Telegram Account Link",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Link your Telegram handle or phone number dynamically. Database backups will be stored safely relative to your account identity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Username or Phone input
                OutlinedTextField(
                    value = phoneOrUser,
                    onValueChange = { 
                        phoneOrUser = it
                        errorText = null 
                    },
                    enabled = !codeSent,
                    label = { Text("Telegram Phone or @Username") },
                    placeholder = { Text("e.g. +91 96969 71627 or @username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (codeSent) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Verification code input
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { 
                            otpCode = it
                            errorText = null 
                        },
                        label = { Text("Telegram Activation Code") },
                        placeholder = { Text("Enter 5-digit verification code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isAuthenticating) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF26A5E4))
                    }
                } else {
                    Button(
                        onClick = {
                            if (phoneOrUser.trim().isEmpty()) {
                                errorText = "Please enter your Telegram Phone or Username"
                                return@Button
                            }
                            if (!codeSent) {
                                onRequestOtp(phoneOrUser.trim()) { success, msg ->
                                    if (success) {
                                        codeSent = true
                                        showToast(msg ?: "Code successfully generated!")
                                    } else {
                                        errorText = msg
                                    }
                                }
                            } else {
                                if (otpCode.trim().isEmpty()) {
                                    errorText = "Please type the verification code"
                                    return@Button
                                }
                                onVerifyCode(phoneOrUser.trim(), otpCode.trim()) { success, msg ->
                                    if (success) {
                                        showToast(msg ?: "Telegram logged in!")
                                        onDismiss()
                                    } else {
                                        errorText = msg
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A5E4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (!codeSent) "Send Activation Code" else "Link Account",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color(0xFF26A5E4))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSupportDialog(
    developerName: String,
    contactNumber: String,
    onDismiss: () -> Unit,
    onCallClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Medical Store Logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .background(MedicalTeal.copy(alpha = 0.12f), CircleShape)
                        .border(3.dp, MedicalTeal, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = "Medical Store Logo",
                        tint = MedicalTeal,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Application Support & Info",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This application is developed and managed cleanly. If you need any technical assistance, please connect with the developer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Developer profile card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = developerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Senior Technical Consultant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Helpline: +91 $contactNumber",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MedicalTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCallClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = "Dial Call")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Place Phone Call", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Panel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Modal Combined Billing Multi-Item Quantity Configuration Dialog
@Composable
fun CombinedQuantityDialog(
    selectedMedicines: List<Medicine>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Int, Int>) -> Unit
) {
    // Local state to store quantities, defaulting to 1 for all items
    var quantities by remember {
        mutableStateOf(selectedMedicines.associate { it.id to 1 })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = MedicalTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configure Billing Quantities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Specify the quantity for each selected medicine to adjust the grand total on the commercial invoice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(selectedMedicines) { medicine ->
                        val currentQty = quantities[medicine.id] ?: 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medicine.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Rate: ₹${String.format(Locale.getDefault(), "%,.2f", medicine.sellPrice)} • Stock: ${medicine.stockQty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // "-" button
                                IconButton(
                                    onClick = {
                                        if (currentQty > 1) {
                                            quantities = quantities.toMutableMap().apply {
                                                put(medicine.id, currentQty - 1)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease Quantity",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = currentQty.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.widthIn(min = 20.dp),
                                    textAlign = TextAlign.Center
                                )

                                // "+" button
                                IconButton(
                                    onClick = {
                                        val limit = if (medicine.stockQty > 0) medicine.stockQty else 999
                                        if (currentQty < limit) {
                                            quantities = quantities.toMutableMap().apply {
                                                put(medicine.id, currentQty + 1)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase Quantity",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(quantities) },
                colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Invoice", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
