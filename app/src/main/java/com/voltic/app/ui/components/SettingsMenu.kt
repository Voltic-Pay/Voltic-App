package com.voltic.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voltic.app.R

@Composable
fun SettingsMenuContent(
    onShowSeed: () -> Unit,
    onSwitchWallet: () -> Unit,
    onNavigateToLimits: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(Modifier.height(24.dp))
        
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Account Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(16.dp)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            NavigationDrawerItem(
                label = { Text("Show Seed Phrase", fontWeight = FontWeight.Medium) },
                selected = false,
                icon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_password),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    ) 
                },
                onClick = {
                    onCloseDrawer()
                    onShowSeed()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                label = { Text("Spending Limits", fontWeight = FontWeight.Medium) },
                selected = false,
                icon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_money_off), // Temporary icon
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    ) 
                },
                onClick = {
                    onCloseDrawer()
                    onNavigateToLimits()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                label = { Text("Switch Wallet", fontWeight = FontWeight.Medium) },
                selected = false,
                icon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_switch),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    ) 
                },
                onClick = {
                    onCloseDrawer()
                    onSwitchWallet()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
