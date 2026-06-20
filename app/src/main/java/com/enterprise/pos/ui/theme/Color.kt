package com.enterprise.pos.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Primary Palette — Blue
// Used for primary actions, navigation, and brand identity.
// =============================================================================
val Primary10 = Color(0xFF001D36)
val Primary20 = Color(0xFF003258)
val Primary30 = Color(0xFF00497D)
val Primary40 = Color(0xFF0061A4)
val Primary50 = Color(0xFF1976D2)
val Primary60 = Color(0xFF3589E2)
val Primary70 = Color(0xFF569DF0)
val Primary80 = Color(0xFF82B1FF)
val Primary90 = Color(0xFFD1E4FF)
val Primary100 = Color(0xFFF0F5FF)

// =============================================================================
// Secondary Palette — Coral/Orange
// Used for secondary actions, promotions, and highlights.
// =============================================================================
val Secondary10 = Color(0xFF2D0D00)
val Secondary20 = Color(0xFF4E1500)
val Secondary30 = Color(0xFF6F2000)
val Secondary40 = Color(0xFF8F2E00)
val Secondary50 = Color(0xFFBF360C)
val Secondary60 = Color(0xFFDA4A1E)
val Secondary70 = Color(0xFFF05A2D)
val Secondary80 = Color(0xFFFFAB91)
val Secondary90 = Color(0xFFFFDCC7)
val Secondary100 = Color(0xFFFFF1EC)

// =============================================================================
// Tertiary Palette — Green
// Used for success states, confirmations, and positive feedback.
// =============================================================================
val Tertiary10 = Color(0xFF00210C)
val Tertiary20 = Color(0xFF003A1E)
val Tertiary30 = Color(0xFF00522E)
val Tertiary40 = Color(0xFF006E3E)
val Tertiary50 = Color(0xFF1B5E20)
val Tertiary60 = Color(0xFF2E7D32)
val Tertiary70 = Color(0xFF43A047)
val Tertiary80 = Color(0xFFA5D6A7)
val Tertiary90 = Color(0xFFC8E6C9)
val Tertiary100 = Color(0xFFE8F5E9)

// =============================================================================
// Neutral Palette — Slate/Grey
// Used for backgrounds, surfaces, text, and dividers.
// =============================================================================
val Neutral0 = Color(0xFF000000)
val Neutral10 = Color(0xFF101418)
val Neutral20 = Color(0xFF21262D)
val Neutral30 = Color(0xFF37474F)
val Neutral40 = Color(0xFF495057)
val Neutral50 = Color(0xFF607D8B)
val Neutral60 = Color(0xFF9E9E9E)
val Neutral70 = Color(0xFFB0BEC5)
val Neutral80 = Color(0xFFE0E0E0)
val Neutral90 = Color(0xFFF1F3F4)
val Neutral100 = Color(0xFFFFFFFF)

// =============================================================================
// Error Palette — Red
// Used for error states, validation failures, and destructive actions.
// =============================================================================
val Error10 = Color(0xFF2A0000)
val Error20 = Color(0xFF4E0002)
val Error30 = Color(0xFF750E0B)
val Error40 = Color(0xFF9A1512)
val Error50 = Color(0xFFB71C1C)
val Error60 = Color(0xFFE53935)
val Error70 = Color(0xFFEF5350)
val Error80 = Color(0xFFFFCDD2)
val Error90 = Color(0xFFFFE7E6)
val Error100 = Color(0xFFFFF5F5)

// =============================================================================
// Success Palette — Green (distinct from Tertiary for dedicated status use)
// Used for success badges, checkmarks, and completed states.
// =============================================================================
val Success10 = Color(0xFF00210C)
val Success20 = Color(0xFF003A1E)
val Success30 = Color(0xFF00522E)
val Success40 = Color(0xFF006E3E)
val Success50 = Color(0xFF0B8C42)
val Success60 = Color(0xFF2E9D5E)
val Success70 = Color(0xFF4DB87A)
val Success80 = Color(0xFF81D4A2)
val Success90 = Color(0xFFCBEDD9)
val Success100 = Color(0xFFE8F5EE)

// =============================================================================
// Warning Palette — Amber/Yellow
// Used for cautions, pending items, and attention-required states.
// =============================================================================
val Warning10 = Color(0xFF2A1500)
val Warning20 = Color(0xFF4A2700)
val Warning30 = Color(0xFF6D3C00)
val Warning40 = Color(0xFF8F5000)
val Warning50 = Color(0xFFB56C00)
val Warning60 = Color(0xFFE89A00)
val Warning70 = Color(0xFFFFB300)
val Warning80 = Color(0xFFFFE082)
val Warning90 = Color(0xFFFFF0C2)
val Warning100 = Color(0xFFFFF8E1)

// =============================================================================
// Info Palette — Cyan/Blue
// Used for informational badges, tips, and neutral status indicators.
// =============================================================================
val Info10 = Color(0xFF001D36)
val Info20 = Color(0xFF003258)
val Info30 = Color(0xFF00497D)
val Info40 = Color(0xFF0061A4)
val Info50 = Color(0xFF0D7EBC)
val Info60 = Color(0xFF2A9AD8)
val Info70 = Color(0xFF4DB4E8)
val Info80 = Color(0xFF8FD3F4)
val Info90 = Color(0xFFC2E8FB)
val Info100 = Color(0xFFE1F3FC)

// =============================================================================
// POS-Specific Semantic Colors
// These are mapped to real-world POS entity states for instant visual
// comprehension by cashiers and floor staff.
// =============================================================================

// Drawer / Cash Register States
val DrawerOpen = Success60       // Green — drawer is open, action expected
val DrawerClosed = Neutral60       // Grey — drawer is closed, idle state

// Table States (restaurant/dining floor)
val TableAvailable = Success60     // Green — table is free and ready
val TableOccupied = Error60        // Red — table has an active order
val TableReserved = Warning70      // Amber — table is reserved for future
val TableDirty = Info60            // Blue — table needs cleaning (busser)

// Order Lifecycle States
val OrderOpen = Primary60          // Blue — order is being edited / open
val OrderPaid = Success60          // Green — order is fully paid
val OrderCancelled = Error60       // Red — order was cancelled
val OrderRefunded = Warning70      // Amber — order was refunded

// Item / Product Availability States
val ItemAvailable = Success60      // Green — item is in stock and orderable
val ItemOutOfStock = Error60       // Red — item is temporarily unavailable
val ItemDiscontinued = Neutral60   // Grey — item is permanently discontinued

// Payment Transaction States
val PaymentPending = Warning70     // Amber — payment is processing / awaiting
val PaymentCompleted = Success60   // Green — payment succeeded
val PaymentFailed = Error60        // Red — payment declined or failed
val PaymentRefunded = Info60       // Blue — refund processed
