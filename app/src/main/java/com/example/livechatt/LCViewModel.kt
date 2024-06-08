package com.example.livechatt

import android.icu.util.Calendar
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.livechatt.data.CHATS
import com.example.livechatt.data.ChatData
import com.example.livechatt.data.ChatUser
import com.example.livechatt.data.Event
import com.example.livechatt.data.MESSAGE
import com.example.livechatt.data.Message
import com.example.livechatt.data.STATUS
import com.example.livechatt.data.Status
import com.example.livechatt.data.USER_NODE
import com.example.livechatt.data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LCViewModel @Inject constructor(
    val auth: FirebaseAuth,
    var db: FirebaseFirestore,
    val storage: FirebaseStorage
): ViewModel() {

    var inProcess = mutableStateOf( false)
    var inProcessChats = mutableStateOf(false)
    val eventMutableState = mutableStateOf<Event<String>?>(null)
    var signIn = mutableStateOf(false)
    val userData = mutableStateOf<UserData?>(null)
    val chats = mutableStateOf<List<ChatData>>(listOf())
    val chatMessages = mutableStateOf<List<Message>>(listOf())
    val inProgressChatMessage = mutableStateOf(false)
    var currentChatMessageListener: ListenerRegistration? = null

    val status = mutableStateOf<List<Status>>(listOf())
    val inProgressStatus = mutableStateOf( false)

    init {

        val currentUser = auth.currentUser
        signIn.value = currentUser != null
        currentUser?.uid?.let {
            getUserData(it)
        }
    }

    fun populateMessages(chatId: String){
        inProgressChatMessage.value =true
        currentChatMessageListener=  db.collection(CHATS).document(chatId).collection(MESSAGE)
            .addSnapshotListener{ value, error ->
                if(error !=null){
                    handleException(error)

                }
                if (value !=null){
                    chatMessages.value =value.documents.mapNotNull {
                        it.toObject<Message>()
                    }.sortedBy { it.timetamp }
                    inProgressChatMessage.value =false
                }
            }

    }

    fun depopulateMessage(){
        chatMessages.value = listOf()
        currentChatMessageListener =null
    }

    fun populateChats(){
        inProcessChats.value =true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId),
            )
        ).addSnapshotListener {
            value, error ->
            if (error != null) {
                handleException(error)
            }
            if (value!=null){
                chats.value = value.documents.mapNotNull {
                    it.toObject<ChatData>()
                }
                inProcessChats.value =false
            }

        }
    }

    fun onSendReply(chatID: String, message: String){
        val time = Calendar.getInstance().time.toString()
        val msg= Message(userData.value?.userId, message,time)
        db.collection(CHATS).document(chatID).collection(MESSAGE).document().set(msg)
    }

    fun signUp(name:String, number:String, email:String, password:String){
        inProcess.value =true
        if(name.isEmpty() or number.isEmpty() or email.isEmpty() or password.isEmpty()){
            handleException(customMessage = "Please  fill All fields ")
            return
        }

        inProcess.value = true
        db.collection(USER_NODE).whereEqualTo("number", number).get().addOnSuccessListener {
            if(it.isEmpty){
                auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener {


                    if (it.isSuccessful){
                        signIn.value = true
                        createOrUpdateProfile(name, number)
                    }else{
                        handleException(it.exception, customMessage =  "Sign Up failed")
                    }
                }
            }else{

                handleException(customMessage = "number Already Exists")
                inProcess.value=false
            }
        }

    }

    fun  loginIn(email: String, password: String){

        if(email.isEmpty() or password.isEmpty()){
            handleException(customMessage =  "Please fill the all field")
            return
        }else{
            inProcess.value = true
            auth.signInWithEmailAndPassword(email,password)
                 .addOnCompleteListener {
                    if (it.isSuccessful){
                        signIn.value = true
                        inProcess.value= false
                        auth.currentUser?.uid?.let {
                            getUserData(it)
                    }

                }else{
                    handleException(exception = it.exception, customMessage = "Login fail")
                }
    }
    }
}


    fun uploadProfileImage(uri: Uri){
        uploadImage(uri){
            createOrUpdateProfile(imageurl = it.toString())
        }

    }

    fun uploadImage(uri: Uri, onSuccess: (Uri)->Unit){
        inProcess.value =true
        val storageRef = storage.reference
        val uuid = UUID.randomUUID()
        val imageRef = storageRef.child("images/$uuid")
        val uploadTask = imageRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            val result = it.metadata?.reference?.downloadUrl

            result?.addOnSuccessListener(onSuccess)
            inProcess.value = false
        }
            .addOnFailureListener {
                handleException(it)
            }
    }


    fun createOrUpdateProfile(
        name: String?= null,
        number: String?= null,
        imageurl: String? = null
    ){
            val uid =auth.currentUser?.uid
            val userData = UserData(
                userId = uid,
                name = name?: userData.value?.name,
                number = number?: userData.value?.number,
                imageUrl = imageurl?: userData.value?.imageUrl)
        uid?.let {uid ->
            inProcess.value = true
            db.collection(USER_NODE).document(uid)
                .get()
                .addOnSuccessListener {
                    if (it.exists()){
                    //update  user
                        it.reference.update(userData.toMap())
                            .addOnSuccessListener {
                                inProcess.value =false
                            }
                            .addOnFailureListener {
                                handleException(it,"Cannot update user")
                            }
                    }else{
                    //create user
                        db.collection(USER_NODE).document(uid).set(userData)
                        inProcess.value =false
                        getUserData(uid)
                    }
                }
                .addOnFailureListener {
                    handleException(it, "Cannot retrieve user")
                }
        }

    }

    private fun getUserData(uid: String) {
        inProcess.value =true
        db.collection(USER_NODE).document(uid).addSnapshotListener { value, error ->
            if (error != null){
                handleException(error, "Can not retrieve User ")
            }
            if (value != null){
                var user = value.toObject<UserData>()
                userData.value = user
                inProcess.value = false
                populateChats()
                populateStatuses()

            }
        }
        
    }

    fun handleException(exception: Exception?= null, customMessage: String =""){
        Log.e("LiveChatApp", "Live chat exception", exception)
        exception?.printStackTrace()
        val errorMsg = exception?.localizedMessage?:""
        val message = if(customMessage.isNullOrEmpty())errorMsg else customMessage

        eventMutableState.value = Event(message)
        inProcess.value =false
    }

    fun logout() {
        auth.signOut()
        signIn.value =false
        userData.value = null
        depopulateMessage()
        currentChatMessageListener= null
        eventMutableState.value = Event("Logged Out")
    }

    fun onAddChat(number: String) {

        if (number.isEmpty()or ! number.isDigitsOnly()){
            handleException(customMessage = "Number must be contain digits only")
        }else{
            db.collection(CHATS).where(Filter.or(
                Filter.and(
                    Filter.equalTo("user1.number",number),
                    Filter.equalTo("user2.number",userData.value?.number)
                ),
                Filter.and(
                    Filter.equalTo("user1.number",userData.value?.number),
                    Filter.equalTo("user2.number",number)
                )



            )).get().addOnSuccessListener {
                if (it.isEmpty){


                    db.collection(USER_NODE).whereEqualTo("number", number).get().addOnSuccessListener {
                        if(it.isEmpty){
                            handleException(customMessage = "Number not found")
                        }else{
                            val chatPartner = it.toObjects<UserData>()[0]
                            val id = db.collection(CHATS).document().id
                            val chat = ChatData(
                                chatId = id,
                                ChatUser(userData.value?.userId,
                                        userData.value?.name,
                                        userData.value?.imageUrl,
                                        userData.value?.number),
                                ChatUser(chatPartner.userId,
                                    chatPartner.name,chatPartner.imageUrl,chatPartner.number
                                )
                            )
                            db.collection(CHATS).document().set(chat)
                        }
                    }
                        .addOnFailureListener {
                            handleException(it)
                    }
                }
                else {
                    handleException(customMessage = "Chat already exists")
                }
            }
        }
    }

    fun uploadStatus(uri: Uri) {
        uploadImage(uri){
            createStatus(it.toString())
        }

    }
    fun createStatus(imageurl:String){
        val newStatus = Status(
            ChatUser(
                userData.value?.userId,
                userData.value?.name,
                userData.value?.imageUrl,
                userData.value?.number,
            ),
            imageurl,
            System.currentTimeMillis()
        )

        db.collection(STATUS).document().set(newStatus)

    }
    fun  populateStatuses(){
        val timeDlata = 24L *60 *60 *1000
        val cutOff = System.currentTimeMillis()- timeDlata
        inProgressStatus.value = true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId)
            )
        ).addSnapshotListener{
            value, error ->
            if (error!=null){
                handleException()
            }
            if (value!=null){
                val currentConnections = arrayListOf(userData.value?.userId)

                val chats = value.toObjects<ChatData>()
                chats.forEach{
                    chat ->
                    if (chat.user1.userId == userData.value?.userId){
                        currentConnections.add(chat.user2.userId)

                    }
                    else
                        currentConnections.add(chat.user1.userId)
                }
                db.collection(STATUS).whereGreaterThan("timestamp", cutOff).whereIn("user.userId", currentConnections)
                    .addSnapshotListener{value, error ->
                        if (error!=null){
                            handleException()
                        }
                        if (value!=null){
                            status.value = value.toObjects()
                            inProgressStatus.value = false
                        }
                    }
            }
        }

    }
}

