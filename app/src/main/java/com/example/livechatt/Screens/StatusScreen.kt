package com.example.livechatt.Screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.livechatt.CommonDivider
import com.example.livechatt.CommonProgressBar
import com.example.livechatt.CommonRow
import com.example.livechatt.DestinationScreen
import com.example.livechatt.LCViewModel
import com.example.livechatt.TitleText
import com.example.livechatt.navigateTo

@Composable
fun StatusScreen(navController: NavController, vm : LCViewModel) {
    val inProcess = vm.inProgressStatus.value
    if (inProcess){
        CommonProgressBar()
    }else{
        val statuses = vm.status.value
        val userData = vm.userData.value

        val myStatuses = statuses.filter {
            it.user.userId == userData?.userId
        }
        val otherStatuses = statuses.filter {
            it.user.userId != userData?.userId
        }
        val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            uri ->
            uri?.let {
                vm.uploadStatus(uri)
            }
            
        }


        Scaffold (
            floatingActionButton = {
                FAB{
                    launcher.launch("image/*")
                }
            },
            content ={
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                ) {
                    TitleText(txt = "Status")
                    if (statuses.isEmpty()){
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "No Statuses available")
                        }
                    }else{
                        if (myStatuses.isNotEmpty()){
                            CommonRow(
                                imageUrl = myStatuses[0].user.imageUrl,
                                name = myStatuses[0].user.name
                            ) {
                                navigateTo(
                                    navController = navController,
                                    DestinationScreen.SingleStatus.createRoute(myStatuses[0].user.userId!!)
                                )
                            }
                            CommonDivider()
                            val uniqueUsers = otherStatuses.map { it.user }.toSet().toList()
                            LazyColumn (modifier = Modifier.weight(1f)){
                                items(uniqueUsers){
                                    user ->
                                    CommonRow(imageUrl = user.imageUrl, name = user.name) {
                                        navigateTo(navController = navController, DestinationScreen.SingleStatus.createRoute(user.userId!!)
                                        )

                                    }
                                }

                            }
                        }


                    }
                    BottomNavigationMenu(
                        selectedItem = BottomNavigationItem.STATUSlIST,
                        navController = navController
                    )

                }

            }
        )


    }
    

}

@Composable
fun FAB(
    onFabClick:()->Unit
){
    FloatingActionButton(
        onClick = onFabClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        shape = CircleShape,
        modifier = Modifier.padding(bottom = 40.dp)
    ) {
        Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Add Status", tint = Color.White)
    }
}