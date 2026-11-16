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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.model.NinevehDistrict
import com.example.data.model.ServiceCategory
import com.example.data.model.SortOption
import com.example.ui.theme.AmberGold
import com.example.ui.theme.IndigoAccentLight
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.StatusOpen
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusUrgent
import com.example.ui.viewmodel.DoriUiState
import com.example.util.IntentHelpers

@Composable
fun ExploreScreen(
    state: DoriUiState,
    onSelectDistrict: (NinevehDistrict) -> Unit,
    onSelectArea: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (ServiceCategory) -> Unit,
    onSelectSpecialty: (MedicalSpecialty) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onBookFastestTurn: () -> Unit,
    onDirectBookVenue: (VenueEntity) -> Unit,
    onVenueClick: (VenueEntity) -> Unit,
    onToggleFavorite: (VenueEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compact Nineveh Hero Banner
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_nineveh_hero),
                            contentDescription = "معالم نينوى والموصل الرقمية",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF070B14).copy(alpha = 0.94f),
                                            Color(0xFF0B1426).copy(alpha = 0.78f),
                                            Color(0xFF070B14).copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "نينوى • ${state.preferences.selectedDistrict.displayName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MintPrimaryDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "حجز بنفس اليوم",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "احجز دورك وأنت في مكانك",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = onBookFastestTurn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimaryDark,
                                    contentColor = Color(0xFF04221B)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("fastest_turn_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⚡ أسرع دور الآن (${state.fastestWaitMinutes} د)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Neo-Bento Instant Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeoStatCard(
                        title = "أماكن مفتوحة",
                        value = "${state.openCountInDistrict}",
                        icon = Icons.Default.Storefront,
                        accent = StatusOpen,
                        modifier = Modifier.weight(1f)
                    )
                    NeoStatCard(
                        title = "أسرع انتظار",
                        value = "${state.fastestWaitMinutes} د",
                        icon = Icons.Default.AccessTime,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    NeoStatCard(
                        title = "متوسط التقييم",
                        value = "${state.averageRatingInDistrict} ★",
                        icon = Icons.Default.Star,
                        accent = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Nineveh Districts Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NinevehDistrict.entries) { district ->
                        val selected = state.preferences.selectedDistrict == district
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectDistrict(district) },
                            label = { Text(district.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("explore_district_chip_${district.name.lowercase()}")
                        )
                    }
                }
            }

            // Neighborhoods Strip within Selected District
            if (state.availableAreas.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedArea == null,
                                onClick = { onSelectArea(null) },
                                label = { Text("كل المناطق", style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                        items(state.availableAreas) { area ->
                            FilterChip(
                                selected = state.selectedArea == area,
                                onClick = { onSelectArea(area) },
                                label = { Text(area, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("area_chip_$area")
                            )
                        }
                    }
                }
            }

            // Search Bar + Favorites Heart Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("ابحث باسم المحل، الحي، أو القضاء...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "بحث")
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("explore_search_input")
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (state.showFavoritesOnly) StatusUrgent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (state.showFavoritesOnly) StatusUrgent.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onToggleFavoritesOnly() }
                            .testTag("favorites_filter_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "المفضلة",
                                tint = if (state.showFavoritesOnly) StatusUrgent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5 Categories + All Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ServiceCategory.entries) { category ->
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

            // Medical Specialties Sub-Filter when Clinics category is selected
            if (state.selectedCategory == ServiceCategory.CLINICS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "التخصص الطبي:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            }

            // Sort Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Text(
                            text = "فرز:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(SortOption.entries) { option ->
                        FilterChip(
                            selected = state.sortOption == option,
                            onClick = { onSortSelect(option) },
                            label = { Text(option.displayName, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}")
                        )
                    }
                }
            }

            // Venues List
            if (state.filteredVenues.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "لا توجد مراكز مطابقة لهذا الحي أو البحث",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "جرّب اختيار \"جميع المناطق\" أو قضاء مجاور في نينوى لعرض المراكز المفتوحة القريبة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.selectedArea != null || state.searchQuery.isNotBlank()) {
                                Button(
                                    onClick = {
                                        onSelectArea(null)
                                        onSearchChange("")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("عرض جميع مراكز ${state.preferences.selectedDistrict.displayName}")
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.filteredVenues, key = { it.id }) { venue ->
                    FuturisticVenueCard(
                        venue = venue,
                        onDirectBook = { onDirectBookVenue(venue) },
                        onToggleFavorite = { onToggleFavorite(venue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeoStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FuturisticVenueCard(
    venue: VenueEntity,
    onDirectBook: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isAvailable = venue.isOpen && venue.isAcceptingBookings
    val statusColor = when {
        !venue.isAcceptingBookings -> StatusUrgent
        venue.isPaused -> StatusPaused
        else -> StatusOpen
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
            // Concise Header: Name, Area + Status dot, Same-Day tag, Favorite
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
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = StatusOpen.copy(alpha = 0.12f)
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

            // Minimal 1-Line Queue + ETA + Price Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دور #${venue.nextTicketNumber}  •  قبلك ${venue.queueAheadCount}  •  " +
                        if (venue.estimatedWaitMinutes <= 0) "فوري" else "${venue.estimatedWaitMinutes} د",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (venue.category == ServiceCategory.FIELDS.displayName) {
                        "${IntentHelpers.formatDinar(venue.priceDinar)}/س"
                    } else {
                        IntentHelpers.formatDinar(venue.priceDinar)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Single Sleek Direct Same-Day Booking CTA
            Button(
                onClick = onDirectBook,
                enabled = isAvailable,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("direct_book_venue_${venue.id}")
            ) {
                Text(
                    text = if (isAvailable) "احجز الآن بنفس اليوم (#${venue.nextTicketNumber})" else "الاستقبال متوقف",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
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
