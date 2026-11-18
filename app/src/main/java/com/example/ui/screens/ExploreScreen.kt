package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VenueEntity
import com.example.data.model.MedicalSpecialty
import com.example.data.model.ServiceCategory
import com.example.data.model.SortOption
import com.example.ui.theme.AmberGold
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState
import com.example.util.IntentHelpers

@Composable
fun ExploreScreen(
    state: DoriUiState,
    onSelectArea: (String?) -> Unit,
    onClearSearch: () -> Unit,
    onCategorySelect: (ServiceCategory) -> Unit,
    onSelectSpecialty: (MedicalSpecialty) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onBookFastestTurn: () -> Unit,
    onDirectBookVenue: (VenueEntity) -> Unit,
    onToggleFavorite: (VenueEntity) -> Unit,
    onOpenSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctrl = state.preferences.controlConfig
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var areaMenuExpanded by remember { mutableStateOf(false) }

    val visibleCategories = remember(ctrl.disabledCategories) {
        ServiceCategory.entries.filter {
            it == ServiceCategory.ALL || it.displayName !in ctrl.disabledCategories
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Optional Admin Broadcast / Maintenance Banner
            if (ctrl.broadcastMessage.isNotBlank() || !ctrl.allowNewBookings) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!ctrl.allowNewBookings) StatusUrgent.copy(alpha = 0.14f)
                        else AmberGold.copy(alpha = 0.16f),
                        border = BorderStroke(
                            1.dp,
                            if (!ctrl.allowNewBookings) StatusUrgent.copy(alpha = 0.45f)
                            else AmberGold.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_control_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = if (!ctrl.allowNewBookings) StatusUrgent else AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (!ctrl.allowNewBookings) {
                                    "الحجوزات متوقفة مؤقتاً حالياً"
                                } else {
                                    ctrl.broadcastMessage
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // Sleek Compact Hero & Instant Metrics Strip (replaces bulky banner + 3 separate boxes)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0A1428), Color(0xFF0D282A), Color(0xFF1E1B4B))
                                )
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_nineveh_hero),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.22f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = state.preferences.selectedDistrict.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MintPrimaryDark.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "بنفس اليوم",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintPrimaryDark,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${state.openCountInDistrict} مفتوح • أسرع انتظار ${state.fastestWaitMinutes} د • ${state.averageRatingInDistrict} ★",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (ctrl.enableFastestTurn) {
                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = onBookFastestTurn,
                                    enabled = ctrl.allowNewBookings,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintPrimaryDark,
                                        contentColor = Color(0xFF04221B)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("fastest_turn_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "أسرع دور",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Categories Row (respects Control Panel enabled categories)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleCategories) { category ->
                        val selected = state.selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category.displayName) },
                            leadingIcon = {
                                categoryIcon(category)?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // Compact Utility Filter Row: Favorites, Sort Dropdown, Area Dropdown
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = state.showFavoritesOnly,
                            onClick = onToggleFavoritesOnly,
                            label = { Text("المفضلة") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (state.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (state.showFavoritesOnly) StatusUrgent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusUrgent.copy(alpha = 0.16f),
                                selectedLabelColor = StatusUrgent
                            ),
                            modifier = Modifier.testTag("favorites_filter_button")
                        )
                    }

                    item {
                        Box {
                            FilterChip(
                                selected = state.sortOption != SortOption.HIGHEST_RATED,
                                onClick = { sortMenuExpanded = true },
                                label = { Text(state.sortOption.displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("sort_dropdown_chip")
                            )

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.displayName,
                                                fontWeight = if (state.sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                color = if (state.sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSortSelect(option)
                                            sortMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }

                    if (state.availableAreas.isNotEmpty()) {
                        item {
                            Box {
                                FilterChip(
                                    selected = state.selectedArea != null,
                                    onClick = { areaMenuExpanded = true },
                                    label = { Text(state.selectedArea ?: "كل الأحياء") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("area_dropdown_chip")
                                )

                                DropdownMenu(
                                    expanded = areaMenuExpanded,
                                    onDismissRequest = { areaMenuExpanded = false },
                                    modifier = Modifier.heightIn(max = 320.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "كل الأحياء",
                                                fontWeight = if (state.selectedArea == null) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onSelectArea(null)
                                            areaMenuExpanded = false
                                        }
                                    )
                                    state.availableAreas.forEach { area ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = area,
                                                    fontWeight = if (state.selectedArea == area) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (state.selectedArea == area) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                onSelectArea(area)
                                                areaMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("area_chip_$area")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Medical Specialties Sub-Filter only when Clinics is selected
            if (state.selectedCategory == ServiceCategory.CLINICS) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(MedicalSpecialty.entries) { specialty ->
                            FilterChip(
                                selected = state.selectedSpecialty == specialty,
                                onClick = { onSelectSpecialty(specialty) },
                                label = { Text(specialty.displayName, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("specialty_chip_${specialty.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Venues List
            if (state.filteredVenues.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "لا توجد مراكز مطابقة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "جرّب تغيير البحث أو إزالة الفلاتر لعرض جميع المراكز المفتوحة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.selectedArea != null || state.searchQuery.isNotBlank() || state.showFavoritesOnly) {
                                Button(
                                    onClick = {
                                        onSelectArea(null)
                                        onClearSearch()
                                        if (state.showFavoritesOnly) onToggleFavoritesOnly()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("إعادة ضبط الفلاتر")
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المراكز المتاحة (${state.filteredVenues.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.selectedArea != null ||
                            state.searchQuery.isNotBlank() ||
                            state.showFavoritesOnly ||
                            state.selectedCategory != ServiceCategory.ALL
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onSelectArea(null)
                                        onClearSearch()
                                        onCategorySelect(ServiceCategory.ALL)
                                        if (state.showFavoritesOnly) onToggleFavoritesOnly()
                                    }
                            ) {
                                Text(
                                    text = "إظهار الكل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                items(state.filteredVenues, key = { it.id }) { venue ->
                    val linkedProvider = state.providers.firstOrNull { it.venueId == venue.id && it.isActive }
                    FuturisticVenueCard(
                        venue = venue,
                        providerLabel = linkedProvider?.let { "${it.name} (${it.customTitle})" },
                        bookingsAllowed = ctrl.allowNewBookings,
                        onDirectBook = { onDirectBookVenue(venue) },
                        onToggleFavorite = { onToggleFavorite(venue) }
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenSupport() }
                            .testTag("explore_support_footer_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Column {
                                    Text(
                                        text = "تحتاج مساعدة أو لديك استفسار؟",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "اضغط هنا للتواصل المباشر مع الدعم الفني",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "الدعم الفني ←",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuturisticVenueCard(
    venue: VenueEntity,
    providerLabel: String? = null,
    bookingsAllowed: Boolean,
    onDirectBook: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isAvailable = bookingsAllowed && venue.isOpen && venue.isAcceptingBookings
    val statusColor = when {
        !bookingsAllowed || !venue.isOpen || !venue.isAcceptingBookings -> StatusUrgent
        venue.isPaused -> StatusPaused
        else -> StatusOpen
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDirectBook() }
            .testTag("venue_card_${venue.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = venue.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${venue.city} • ${venue.area}" +
                            if (venue.category == ServiceCategory.CLINICS.displayName && venue.specialty.isNotBlank()) " • ${venue.specialty}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!providerLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = StatusOpen,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = providerLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusOpen,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AmberGold.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "★ ${venue.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .testTag("favorite_toggle_${venue.id}")
                    ) {
                        Icon(
                            imageVector = if (venue.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "المفضلة",
                            tint = if (venue.isFavorite) StatusUrgent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Compact Queue Info + Price + Inline Book Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "دور #${venue.nextTicketNumber}  •  قبلك ${venue.queueAheadCount}  •  " +
                            if (venue.estimatedWaitMinutes <= 0) "فوري" else "${venue.estimatedWaitMinutes} د",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (venue.category == ServiceCategory.FIELDS.displayName) {
                            "${IntentHelpers.formatDinar(venue.priceDinar)}/س"
                        } else {
                            IntentHelpers.formatDinar(venue.priceDinar)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDirectBook,
                    enabled = isAvailable,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("direct_book_venue_${venue.id}")
                ) {
                    Text(
                        text = if (isAvailable) "احجز الآن" else "متوقف",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun categoryIcon(category: ServiceCategory): ImageVector? = when (category) {
    ServiceCategory.ALL -> null
    ServiceCategory.CLINICS -> Icons.Default.LocalHospital
    ServiceCategory.MAINTENANCE -> Icons.Default.Build
    ServiceCategory.BEAUTY -> Icons.Default.Spa
    ServiceCategory.FIELDS -> Icons.Default.SportsSoccer
    ServiceCategory.LABS -> Icons.Default.Science
}
