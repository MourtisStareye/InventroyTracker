package com.example.inventorytracker.ui.edit

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.inventorytracker.ui.components.DeleteConfirmationDialog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CommonCategories = listOf(
    "Groceries",
    "Pantry",
    "Beverages",
    "Electronics",
    "Household",
    "Personal Care",
    "Office Supplies",
    "Books",
    "Clothing",
    "Miscellaneous"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    viewModel: ItemEditViewModel,
    openPhotoPickerOnLaunch: Boolean = false,
    onBackClick: () -> Unit,
    onScanBarcodeClick: () -> Unit,
    onSaved: (itemId: Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barcode by viewModel.barcode.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val brand by viewModel.brand.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val quantity by viewModel.quantity.collectAsStateWithLifecycle()
    val imageUrl by viewModel.imageUrl.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            coroutineScope.launch {
                val savedPhoto = withContext(Dispatchers.IO) {
                    runCatching {
                        val photoDirectory = File(context.filesDir, "item_photos").apply { mkdirs() }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(selectedUri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        } ?: error("Could not open the selected photo.")
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Selected file is not a supported image.")
                        val sample = maxOf(bounds.outWidth, bounds.outHeight).let { dimension ->
                            var factor = 1
                            while (dimension / factor > 1600) factor *= 2
                            factor
                        }
                        val bitmap = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                                inSampleSize = sample
                            })
                        } ?: error("Could not read the selected photo.")
                        val destination = File(photoDirectory, "${UUID.randomUUID()}.jpg")
                        destination.outputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                                error("Could not compress the selected photo.")
                            }
                        }
                        bitmap.recycle()
                        destination
                    }
                }
                savedPhoto.onSuccess { file ->
                    viewModel.updateImageUrl(Uri.fromFile(file).toString())
                    photoError = null
                }.onFailure {
                    photoError = "Unable to save the selected photo. Choose another image."
                }
            }
        }
    }

    val isEditing = viewModel.existingItemId > 0

    LaunchedEffect(openPhotoPickerOnLaunch) {
        if (openPhotoPickerOnLaunch && !isEditing) photoPicker.launch("image/*")
    }

    if (showDeleteDialog && isEditing) {
        DeleteConfirmationDialog(
            itemName = name.ifBlank { "Item" },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteItem(onDeleted = onDeleted)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Item" else "Add Item",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete Item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { viewModel.saveItem(onSaved = onSaved) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "Save Changes" else "Add to Inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (!isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image Preview Card (if an uploaded photo or image URL is provided)
                if (imageUrl.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Product Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Unable to load image",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Barcode Field
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { viewModel.updateBarcode(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Barcode / UPC") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.QrCode,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onScanBarcodeClick) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )

                // Item Name (Required)
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product Name *") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Label,
                            contentDescription = null
                        )
                    },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(text = it) } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // Brand Field
                OutlinedTextField(
                    value = brand,
                    onValueChange = { viewModel.updateBrand(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brand / Manufacturer") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // Category Field + Suggestion Chips
                Column {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { viewModel.updateCategory(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Category") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Suggested Categories:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CommonCategories.forEach { suggested ->
                            FilterChip(
                                selected = category.equals(suggested, ignoreCase = true),
                                onClick = { viewModel.updateCategory(suggested) },
                                label = { Text(suggested) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Quantity Field + Stepper
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Numbers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Quantity",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$quantity",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.updateQuantity(-1) },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease Quantity"
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { viewModel.updateQuantity(1) },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase Quantity"
                                )
                            }
                        }
                    }
                }

                // Image URL Field
                OutlinedButton(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (imageUrl.startsWith("file:")) "Change photo" else "Choose photo from phone")
                }
                photoError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { viewModel.updateImageUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Image URL") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )

                // Location Field
                OutlinedTextField(
                    value = location,
                    onValueChange = { viewModel.updateLocation(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Location (e.g. Shelf A, Pantry Top)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // Price Field
                OutlinedTextField(
                    value = price,
                    onValueChange = { viewModel.updatePrice(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Price ($)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PriceCheck,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                )

                // Notes / Description
                OutlinedTextField(
                    value = notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    label = { Text("Notes / Description") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Notes,
                            contentDescription = null
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
