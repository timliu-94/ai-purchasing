package com.aipurchase.app

import android.app.*
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.view.*
import android.widget.*
import org.json.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object { private const val REQUEST_CAMERA=42; private const val REQUEST_GALLERY=43 }
    private val green=Color.rgb(39,107,75);
 private val deep=Color.rgb(20,62,45);
 private val muted=Color.rgb(105,120,111)
    private val worker=Executors.newSingleThreadExecutor();
 private lateinit var root:LinearLayout
    private var activeScroll:ScrollView?=null
    private var keyboardHeight=0
    private val prefs by lazy{getSharedPreferences("fieldledger",MODE_PRIVATE)}
    private var token="";
 private var french=false;
 private var farmers=JSONArray();
 private var products=JSONArray();
 private var purchases=JSONArray()
    private var selectedFarmer=0;
 private val photos=mutableListOf<String>();
 private var draft=JSONObject();
 private var signature:SignatureView?=null
    private var photoGallery:LinearLayout?=null
    private val fr=mapOf("Farm purchases" to "Achats agricoles","Sign in" to "Se connecter","Username" to "Nom d’utilisateur","Password" to "Mot de passe","New Purchase" to "Nouvel achat","Farmers" to "Agriculteurs","Purchase Records" to "Achats","Welcome back" to "Bienvenue","Select farmer" to "Choisir l’agriculteur","Product" to "Produit","Purchase date" to "Date d’achat","Weight" to "Poids","Number of bags" to "Nombre de sacs","Notes" to "Remarques","Take photo" to "Prendre une photo","Choose from gallery" to "Choisir dans la galerie","Review purchase" to "Vérifier l’achat","Quality" to "Qualité","Confirm & sign" to "Confirmer et signer","Farmer signature" to "Signature de l’agriculteur","Clear" to "Effacer","Confirm signature" to "Confirmer la signature","Purchase receipt" to "Reçu d’achat","Done" to "Terminé","Add farmer" to "Ajouter un agriculteur","Farmer name" to "Nom de l’agriculteur","Village / area" to "Village / région","Phone number" to "Téléphone","Save farmer" to "Enregistrer","Search" to "Rechercher","Logout" to "Déconnexion","Today" to "Aujourd’hui","Last 7 days" to "7 derniers jours","This month" to "Ce mois-ci")
    private fun t(s:String)=if(french)fr[s]?:s else s;
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(b:Bundle?){super.onCreate(b);
window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
token=prefs.getString("token","")!!;
french=prefs.getBoolean("fr",false);
root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setBackgroundColor(Color.rgb(248,250,248))};
setContentView(root);
root.viewTreeObserver.addOnGlobalLayoutListener{val visible=Rect();window.decorView.getWindowVisibleDisplayFrame(visible);
val hidden=(window.decorView.rootView.height-visible.bottom).coerceAtLeast(0);
val next=if(hidden>window.decorView.rootView.height/5)hidden else 0;
if(next!=keyboardHeight){keyboardHeight=next;activeScroll?.setPadding(0,0,0,if(next>0)next+dp(32) else dp(24));
if(next>0)scrollFocusedField()}};
if(token.isBlank())login()else load{home()}}
    private fun tx(s:String,z:Float=15f,c:Int=deep,b:Boolean=false)=TextView(this).apply{text=s;
textSize=z;
setTextColor(c);
if(b)setTypeface(typeface,Typeface.BOLD);
setPadding(0,dp(4),0,dp(4))}
    private fun btn(s:String,primary:Boolean=true,go:()->Unit)=Button(this).apply{text=t(s);
isAllCaps=false;
textSize=15f;
setTextColor(if(primary)Color.WHITE else green);
backgroundTintList=android.content.res.ColorStateList.valueOf(if(primary)green else Color.rgb(228,239,233));
setOnClickListener{go()};
layoutParams=LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(9)}}
    private fun field(h:String,type:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{hint=t(h);
inputType=type;
backgroundTintList=android.content.res.ColorStateList.valueOf(green);
setOnFocusChangeListener{v,focused->if(focused)v.postDelayed({v.requestRectangleOnScreen(Rect(0,0,v.width,v.height+dp(24)),true)},300)}}
    private fun scrollFocusedField(){val focused=currentFocus?:return;val scroll=activeScroll?:return;
focused.postDelayed({val target=Rect(0,0,focused.width,focused.height);scroll.offsetDescendantRectToMyCoords(focused,target);
val safeBottom=scroll.height-dp(32);if(target.bottom>safeBottom)scroll.smoothScrollBy(0,target.bottom-safeBottom)},120)}
    private fun inputLabel(host:LinearLayout,label:String){host.addView(tx(t(label),12f,muted,true).apply{setPadding(0,dp(10),0,0)})}
    private fun unitInput(input:EditText,unit:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;
addView(input,LinearLayout.LayoutParams(0,-2,1f));
if(unit.isNotBlank())addView(tx(unit,15f,deep,true).apply{gravity=Gravity.CENTER;setPadding(dp(12),0,dp(8),0)},LinearLayout.LayoutParams(-2,dp(52)))}
    private fun page():LinearLayout{root.removeAllViews();
val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;
setPadding(dp(20),dp(8),dp(20),dp(6));
setBackgroundColor(Color.WHITE)};
bar.addView(tx("FIELDLEDGER",16f,green,true),LinearLayout.LayoutParams(0,dp(50),1f));
bar.addView(tx(if(french) "EN" else "FR",13f,green,true).apply{gravity=Gravity.CENTER;
setOnClickListener{french=!french;
prefs.edit().putBoolean("fr",french).apply();
if(token.isBlank())login()else home()}},LinearLayout.LayoutParams(dp(50),dp(50)));
root.addView(bar);
val scroll=ScrollView(this).apply{clipToPadding=false;isFillViewport=true;setPadding(0,0,0,if(keyboardHeight>0)keyboardHeight+dp(32) else dp(24))};
activeScroll=scroll;
val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setPadding(dp(20),dp(18),dp(20),dp(38))};
scroll.addView(c);
root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));
return c}
    private fun title(c:LinearLayout,s:String,sub:String=""){c.addView(tx(t(s),29f,deep,true));
if(sub.isNotBlank())c.addView(tx(t(sub),14f,muted))}
    private fun back(c:LinearLayout,s:String,go:()->Unit){val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};
r.addView(tx("‹",38f,green).apply{setOnClickListener{go()}},LinearLayout.LayoutParams(dp(45),dp(52)));
r.addView(tx(t(s),23f,deep,true));
c.addView(r)}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun request(method:String,path:String,data:JSONObject?=null,fail:((Int,String)->Unit)?=null,ok:(JSONObject)->Unit){worker.execute{try{val h=URL("${BuildConfig.API_BASE_URL}$path").openConnection() as HttpURLConnection;
h.requestMethod=method;
h.setRequestProperty("Content-Type","application/json");
if(token.isNotBlank())h.setRequestProperty("Authorization","Bearer $token");
if(data!=null){h.doOutput=true;
h.outputStream.use{it.write(data.toString().toByteArray())}};
val code=h.responseCode;
val raw=(if(code<400)h.inputStream else h.errorStream).bufferedReader().readText();
runOnUiThread{if(code<400)ok(if(raw.trim().startsWith("["))JSONObject().put("items",JSONArray(raw))else JSONObject(raw))else{val message=JSONObject(raw).optString("message","Request failed");if(fail!=null)fail(code,message)else toast(message)}}}catch(e:Exception){runOnUiThread{if(fail!=null)fail(0,"Server unavailable")else toast("Server unavailable · ${BuildConfig.API_BASE_URL}")}}}}
    private fun load(ok:()->Unit){val reset:(Int,String)->Unit={_,message->token="";prefs.edit().remove("token").apply();toast(message);login()};
request("GET","/api/v1/farmers",fail=reset){farmers=it.getJSONArray("items");
request("GET","/api/v1/products",fail=reset){products=it.getJSONArray("items");
request("GET","/api/v1/purchases",fail=reset){purchases=it.getJSONArray("items");
ok()}}}}
    private fun login(){val c=page();
c.setPadding(dp(28),dp(55),dp(28),dp(30));
c.addView(tx("FL",38f,green,true));
title(c,"Farm purchases","Simple records. Clear history.");
c.addView(Space(this),LinearLayout.LayoutParams(1,dp(30)));
val u=field("Username");
val p=field("Password",129);
c.addView(u);
c.addView(p);
c.addView(btn("Sign in"){request("POST","/api/v1/auth/login",JSONObject().put("username",u.text.toString()).put("password",p.text.toString())){token=it.getString("token");
prefs.edit().putString("token",token).apply();
load{home()}}});
}
    private fun home(){val c=page();
title(c,"Welcome back","Keep every farm-gate purchase in one place.");
val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setPadding(dp(20),dp(15),dp(20),dp(15));
setBackgroundColor(deep)};
hero.addView(tx("TODAY",11f,Color.rgb(190,213,201),true));
hero.addView(tx("${purchases.length()} records ready",23f,Color.WHITE,true));
c.addView(hero,LinearLayout.LayoutParams(-1,dp(95)).apply{topMargin=dp(20)});
c.addView(btn("New Purchase"){purchase()});
c.addView(btn("Farmers",false){farmerList()});
c.addView(btn("Purchase Records",false){records()});
c.addView(btn("Logout",false){token="";
prefs.edit().remove("token").apply();
login()})}
    private fun farmerList(){val c=page();
back(c,"Farmers"){home()};
val q=field("Search");
c.addView(q);
c.addView(btn("Add farmer"){farmerForm()});
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};
c.addView(list);
fun draw(s:String){list.removeAllViews();
for(i in 0 until farmers.length()){val f=farmers.getJSONObject(i);
if(!matchesFarmerSearch(s,listOf(f.optString("name"),f.optString("phone"),f.optString("farmerNo"),f.optString("village"),f.optString("idNumber"))))continue;
val v=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setPadding(dp(15),dp(10),dp(15),dp(10));
setBackgroundColor(Color.WHITE);
setOnClickListener{farmerDetail(f)}};
v.addView(tx(f.getString("name"),17f,deep,true));
v.addView(tx("${f.getString("farmerNo")} · ${f.getString("village")}",13f,muted));
list.addView(v,LinearLayout.LayoutParams(-1,dp(75)).apply{topMargin=dp(9)})}};
draw("");
q.addTextChangedListener(SimpleWatcher{draw(it)})}
    private fun farmerDetail(f:JSONObject){val c=page();
back(c,f.getString("name")){farmerList()};
title(c,f.getString("farmerNo"),f.getString("village"));
c.addView(tx(f.optString("phone","—")));
c.addView(tx(f.optString("notes"),14f,muted));
c.addView(btn("New Purchase"){selectedFarmer=f.getInt("id");
purchase()});
c.addView(tx(t("Purchase Records"),18f,deep,true));
for(i in 0 until purchases.length()){val p=purchases.getJSONObject(i);
if(p.getInt("farmerId")==f.getInt("id"))c.addView(purchaseCard(p){receipt(p)})}}
    private fun farmerForm(fromPurchase:Boolean=false){val c=page();
back(c,"Add farmer"){if(fromPurchase)purchase()else farmerList()};
val n=field("Farmer name");
val v=field("Village / area");
val ph=field("Phone number",InputType.TYPE_CLASS_PHONE);
val no=field("Notes");
listOf(n,v,ph,no).forEach{c.addView(it)};
c.addView(btn("Save farmer"){if(n.text.isBlank()||v.text.isBlank())toast("Name and village are required")else request("POST","/api/v1/farmers",JSONObject().put("name",n.text.toString()).put("village",v.text.toString()).put("phone",ph.text.toString()).put("notes",no.text.toString())){selectedFarmer=it.getInt("id");
load{if(fromPurchase)purchase()else farmerList()}}})}
    private fun purchase(){val c=page();
back(c,"New Purchase"){home()};
if(farmers.length()==0){c.addView(tx("Add a farmer before creating a purchase."));
c.addView(btn("Add farmer"){farmerForm(true)});
return};
c.addView(tx(t("Select farmer"),12f,muted,true));
if(selectedFarmer<=0||find(farmers,selectedFarmer).length()==0)selectedFarmer=farmers.getJSONObject(0).getInt("id");
val farmerPicker=btn(farmerLabel(find(farmers,selectedFarmer)),false){};
farmerPicker.setOnClickListener{showFarmerPicker(selectedFarmer){id->selectedFarmer=id;farmerPicker.text=farmerLabel(find(farmers,id))}};
c.addView(farmerPicker);
c.addView(btn("Add farmer",false){farmerForm(true)});
val ps=Spinner(this);
ps.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,(0 until products.length()).map{products.getJSONObject(it).getString(if(french)"nameFr" else "nameEn")});
	c.addView(tx(t("Product"),12f,muted,true));
	c.addView(ps);
	c.addView(tx("Product photos *",12f,muted,true));
	photoGallery=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};
	c.addView(photoGallery);
	renderGallery(photoGallery!!,true);
val day=field("YYYY-MM-DD").apply{setText(LocalDate.now().toString())};
val weight=field("0.00",8194);
val bags=field("0",2);
val notes=field("Optional notes");
inputLabel(c,"Purchase date");c.addView(day);
inputLabel(c,"Weight");val weightUnit=tx(products.getJSONObject(ps.selectedItemPosition).optString("defaultUnit","kg"),15f,deep,true).apply{gravity=Gravity.CENTER;setPadding(dp(12),0,dp(8),0)};
val weightRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;addView(weight,LinearLayout.LayoutParams(0,-2,1f));addView(weightUnit,LinearLayout.LayoutParams(-2,dp(52)))};c.addView(weightRow);
inputLabel(c,"Number of bags");c.addView(bags);
inputLabel(c,"Notes");c.addView(notes);
c.addView(tx(t("Quality"),18f,deep,true));
val quality=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};
c.addView(quality);
val inputs=mutableMapOf<Int,EditText>();
fun qualityAt(pos:Int){quality.removeAllViews();
inputs.clear();
val a=products.getJSONObject(pos).getJSONArray("indicators");
for(i in 0 until a.length()){val ind=a.getJSONObject(i);
val label=ind.getString(if(french)"nameFr" else "nameEn");
inputLabel(quality,label);
val e=field(if(ind.getString("dataType")=="NUMBER")"Enter value" else "Enter ${label.lowercase()}",if(ind.getString("dataType")=="NUMBER")8194 else 1);
quality.addView(unitInput(e,ind.optString("unit")));
inputs[ind.getInt("id")]=e}};
ps.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(a:AdapterView<*>?,v:View?,p:Int,id:Long){qualityAt(p);weightUnit.text=products.getJSONObject(p).optString("defaultUnit","kg")};
override fun onNothingSelected(a:AdapterView<*>?){}};
c.addView(btn("Review purchase"){val w=weight.text.toString().toDoubleOrNull();
if(w==null||w<=0||photos.isEmpty()){toast("Weight and one photo are required");
return@btn};
val p=products.getJSONObject(ps.selectedItemPosition);
val inds=p.getJSONArray("indicators");
val values=JSONArray();
for(i in 0 until inds.length()){val ind=inds.getJSONObject(i);
val value=inputs[ind.getInt("id")]?.text.toString();
if(value.isBlank()){toast("Complete all quality fields");
return@btn};
values.put(JSONObject().put("indicatorId",ind.getInt("id")).put("name",ind.getString("nameEn")).put("unit",ind.optString("unit")).put("value",value))};
draft=JSONObject().put("farmerId",selectedFarmer).put("productId",p.getInt("id")).put("purchaseDate",day.text.toString()).put("weight",w).put("unit",p.optString("defaultUnit","kg")).put("bagCount",bags.text.toString().toIntOrNull()?:0).put("notes",notes.text.toString()).put("qualityValues",values).put("images",JSONArray(photos));
prefs.edit().putString("draft",draft.toString()).apply();
review()})}
    override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(result!=RESULT_OK)return;when(r){
REQUEST_CAMERA->{val b=data?.extras?.get("data") as? Bitmap?:return;addPhoto(b)}
REQUEST_GALLERY->{val uris=mutableListOf<Uri>();data?.data?.let{uris.add(it)};data?.clipData?.let{clip->for(i in 0 until clip.itemCount)uris.add(clip.getItemAt(i).uri)};uris.forEach{uri->decodeSelectedImage(uri)?.let{addPhoto(it,false)}};photoGallery?.let{renderGallery(it,true)}}}}
    private fun addPhoto(image:Bitmap,render:Boolean=true){val out=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,85,out);photos.add("data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));if(render)photoGallery?.let{renderGallery(it,true)}}
    private fun decodeSelectedImage(uri:Uri):Bitmap?=try{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)};var sample=1;while(bounds.outWidth/sample>1600||bounds.outHeight/sample>1600)sample*=2;contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}}catch(e:Exception){toast("Unable to read selected photo");null}

    private fun bitmap(dataUrl:String):Bitmap{
        val bytes=Base64.decode(dataUrl.substringAfter(','),Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
    }
    private fun renderGallery(host:LinearLayout,editable:Boolean){
        host.removeAllViews()
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val shown=if(photos.size>4)3 else photos.size
        for(i in 0 until shown){
            val image=ImageView(this).apply{setImageBitmap(bitmap(photos[i]));scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Product photo ${i+1}";setOnClickListener{showAllPhotos()}}
            row.addView(image,LinearLayout.LayoutParams(dp(78),dp(78)).apply{marginEnd=dp(8)})
        }
        if(photos.size>4){
            row.addView(TextView(this).apply{text="+${photos.size-3}\nView all";gravity=Gravity.CENTER;textSize=13f;setTextColor(Color.WHITE);setBackgroundColor(deep);setOnClickListener{showAllPhotos()}},LinearLayout.LayoutParams(dp(78),dp(78)).apply{marginEnd=dp(8)})
        }
        if(editable){
            row.addView(TextView(this).apply{text="＋";gravity=Gravity.CENTER;textSize=34f;setTextColor(green);setBackgroundColor(Color.rgb(230,239,234));contentDescription="Add product photo";setOnClickListener{showPhotoSource()}},LinearLayout.LayoutParams(dp(78),dp(78)))
        }
        host.addView(row,LinearLayout.LayoutParams(-1,dp(86)).apply{topMargin=dp(6)})
        host.addView(tx(if(photos.isEmpty())"Tap + to take the first photo" else "${photos.size} photo${if(photos.size==1)"" else "s"} added",12f,if(photos.isEmpty())muted else green,true))
    }
    private fun showPhotoSource(){AlertDialog.Builder(this).setTitle("Add product photo").setItems(arrayOf(t("Take photo"),t("Choose from gallery"))){_,which->if(which==0)try{startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),REQUEST_CAMERA)}catch(e:Exception){toast("Camera unavailable")}else try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)},REQUEST_GALLERY)}catch(e:Exception){toast("Gallery unavailable")}}.show()}
    private fun farmerLabel(f:JSONObject)="${f.optString("name")} · ${f.optString("farmerNo")}";
    private fun showFarmerPicker(currentId:Int,onSelected:(Int)->Unit){val host=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),dp(8))};val search=field("Search name, phone, Farmer ID or village");val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};host.addView(search);host.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,dp(360)));val dialog=AlertDialog.Builder(this).setTitle(t("Select farmer")).setView(host).setNegativeButton("Cancel",null).create();fun draw(query:String){list.removeAllViews();var matches=0;for(i in 0 until farmers.length()){val f=farmers.getJSONObject(i);if(!matchesFarmerSearch(query,listOf(f.optString("name"),f.optString("phone"),f.optString("farmerNo"),f.optString("village"),f.optString("idNumber"))))continue;matches++;list.addView(TextView(this).apply{text=farmerLabel(f)+(if(f.getInt("id")==currentId)"  ✓" else "");textSize=16f;setTextColor(deep);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener{onSelected(f.getInt("id"));dialog.dismiss()}},LinearLayout.LayoutParams(-1,dp(58)))};if(matches==0)list.addView(tx("No farmers found",14f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(28),0,dp(28))})};search.addTextChangedListener(SimpleWatcher{draw(it)});draw("");dialog.show();search.requestFocus()}
    private fun showAllPhotos(){
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(16))}
        photos.forEachIndexed{i,p->list.addView(ImageView(this).apply{setImageBitmap(bitmap(p));scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Product photo ${i+1}"},LinearLayout.LayoutParams(-1,dp(260)).apply{bottomMargin=dp(12)})}
        AlertDialog.Builder(this).setTitle("Product photos (${photos.size})").setView(ScrollView(this).apply{addView(list)}).setPositiveButton("Close",null).show()
    }
    private fun review(){val c=page();
back(c,"Review purchase"){purchase()};
title(c,"Review purchase","Check the details together before signing.");
summary(c,find(farmers,draft.getInt("farmerId")),find(products,draft.getInt("productId")),draft);
c.addView(tx("Product photos",11f,muted,true));
val preview=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};
c.addView(preview);
renderGallery(preview,false);
c.addView(btn("Confirm & sign"){request("POST","/api/v1/purchases",draft){draft=it;
sign()}})}
    private fun summary(c:LinearLayout,f:JSONObject,p:JSONObject,d:JSONObject){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setPadding(dp(18),dp(14),dp(18),dp(14));
setBackgroundColor(Color.WHITE)};
listOf("Farmer" to "${f.optString("name")} · ${f.optString("farmerNo")}","Product" to p.optString(if(french)"nameFr" else "nameEn"),"Purchase date" to d.optString("purchaseDate"),"Weight" to "${d.opt("weight")} ${d.optString("unit","kg")}","Notes" to d.optString("notes","—")).forEach{b.addView(tx(t(it.first),11f,muted,true));
b.addView(tx(it.second,17f,deep,true))};
val q=d.optJSONArray("qualityValues")?:JSONArray();
b.addView(tx(t("Quality"),11f,muted,true));
for(i in 0 until q.length()){val v=q.getJSONObject(i);
b.addView(tx("${v.optString("name")}: ${v.optString("value")}${v.optString("unit")}"))};
c.addView(b,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18)})}
    private fun sign(){val c=page();
back(c,"Farmer signature"){review()};
title(c,"Farmer signature","I confirm that the information above is correct.");
signature=SignatureView();
c.addView(signature,LinearLayout.LayoutParams(-1,dp(280)).apply{topMargin=dp(20)});
c.addView(btn("Clear",false){signature?.clear()});
c.addView(btn("Confirm signature"){if(signature!!.empty)toast("Signature is required")else request("POST","/api/v1/purchases/${draft.getInt("id")}/signature",JSONObject().put("dataUrl",signature!!.dataUrl())){request("POST","/api/v1/purchases/${draft.getInt("id")}/confirm",JSONObject()){draft=it;
photos.clear();
prefs.edit().remove("draft").apply();
load{receipt(draft)}}}})}
    private fun loadMedia(key:String,done:(Bitmap?)->Unit){worker.execute{val result=try{val encoded=URLEncoder.encode(key,"UTF-8");
val h=URL("${BuildConfig.API_BASE_URL}/api/v1/media?key=$encoded").openConnection() as HttpURLConnection;
h.setRequestProperty("Authorization","Bearer $token");
h.connectTimeout=10000;
h.readTimeout=15000;
if(h.responseCode<400)h.inputStream.use{BitmapFactory.decodeStream(it)}else null}catch(e:Exception){null};
runOnUiThread{done(result)}}}
    private fun remoteImage(key:String,height:Int=180):ImageView=ImageView(this).apply{setBackgroundColor(Color.rgb(232,238,234));
scaleType=ImageView.ScaleType.CENTER_CROP;
contentDescription="Loading image";
setImageResource(android.R.drawable.ic_menu_gallery);
loadMedia(key){image->if(image!=null){setImageBitmap(image);contentDescription="Preview image";setOnClickListener{showImage(image)}}else{contentDescription="Image unavailable"}};
layoutParams=LinearLayout.LayoutParams(-1,dp(height)).apply{topMargin=dp(8)}}
    private fun showImage(image:Bitmap){val preview=ImageView(this).apply{setImageBitmap(image);adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.BLACK)};
AlertDialog.Builder(this).setView(preview).setPositiveButton("Close",null).show()}
    private fun receipt(p:JSONObject){val c=page();
title(c,"Purchase receipt",p.optString("purchaseNo"));
summary(c,find(farmers,p.getInt("farmerId")),find(products,p.getInt("productId")),p);
c.addView(tx("Product photos",16f,deep,true).apply{setPadding(0,dp(18),0,dp(4))});
val images=p.optJSONArray("images")?:JSONArray();
if(images.length()==0)c.addView(tx("No product photos",13f,muted))else for(i in 0 until images.length())c.addView(remoteImage(images.optString(i)));
c.addView(tx("Farmer signature",16f,deep,true).apply{setPadding(0,dp(18),0,dp(4))});
val signatureKey=p.optJSONObject("signature")?.optString("dataUrl").orEmpty();
if(signatureKey.isBlank())c.addView(tx("No signature",13f,muted))else c.addView(remoteImage(signatureKey,140));
c.addView(tx("✓ ${p.optString("status")}",16f,green,true).apply{gravity=Gravity.CENTER});
c.addView(tx("Please take a photo of this receipt.",14f,muted).apply{gravity=Gravity.CENTER});
c.addView(btn("Done"){home()})}
    private fun records(){val c=page();
back(c,"Purchase Records"){home()};
val search=field("Search purchase no., farmer, product...");
val from=field("From date (YYYY-MM-DD)");
val to=field("To date (YYYY-MM-DD)");
val farmer=Spinner(this);
val farmerOptions=listOf("All farmers")+(0 until farmers.length()).map{val f=farmers.getJSONObject(it);"${f.optString("name")} · ${f.optString("farmerNo")}"};
farmer.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,farmerOptions);
val product=Spinner(this);
val productOptions=listOf("All products")+(0 until products.length()).map{products.getJSONObject(it).optString(if(french)"nameFr" else "nameEn")};
product.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,productOptions);
c.addView(search);
val dates=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};
fun dateField(input:EditText)=input.apply{setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_today,0);compoundDrawablePadding=dp(8);contentDescription="${hint}. Tap the calendar icon to choose a date";setOnTouchListener{_,event->val icon=compoundDrawables[2];if(event.action==MotionEvent.ACTION_UP&&icon!=null&&event.x>=width-paddingEnd-icon.bounds.width()){showDatePicker(this);true}else false}};
dates.addView(dateField(from),LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(6)});
dates.addView(dateField(to),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(6)});
c.addView(dates);
val quickDates=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("Today" to {val d=LocalDate.now();from.setText(d.toString());to.setText(d.toString())},"Last 7 days" to {val d=LocalDate.now();from.setText(d.minusDays(6).toString());to.setText(d.toString())},"This month" to {val d=LocalDate.now();from.setText(d.withDayOfMonth(1).toString());to.setText(d.toString())},"Clear" to {from.setText("");to.setText("")}).forEach{(label,action)->quickDates.addView(Button(this).apply{text=t(label);isAllCaps=false;textSize=11f;setTextColor(green);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(228,239,233));setOnClickListener{action()}},LinearLayout.LayoutParams(0,dp(46),1f).apply{marginEnd=dp(3)})};c.addView(quickDates);
c.addView(tx("Farmer",12f,muted,true));c.addView(farmer);
c.addView(tx("Product",12f,muted,true));c.addView(product);
val count=tx("",13f,muted,true).apply{setPadding(0,dp(14),0,dp(4))};c.addView(count);
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(list);
fun draw(){list.removeAllViews();var shown=0;
for(i in 0 until purchases.length()){val p=purchases.getJSONObject(i);val f=find(farmers,p.getInt("farmerId"));val pr=find(products,p.getInt("productId"));
val fields=listOf(p.optString("purchaseNo"),p.optString("status"),p.optString("notes"),f.optString("name"),f.optString("farmerNo"),f.optString("phone"),f.optString("village"),pr.optString("nameEn"),pr.optString("nameFr"));
if(!matchesPurchaseFilters(search.text.toString(),from.text.toString(),to.text.toString(),if(farmer.selectedItemPosition==0)null else farmers.getJSONObject(farmer.selectedItemPosition-1).getInt("id"),if(product.selectedItemPosition==0)null else products.getJSONObject(product.selectedItemPosition-1).getInt("id"),p.optString("purchaseDate"),p.getInt("farmerId"),p.getInt("productId"),fields))continue;
shown++;list.addView(purchaseCard(p){receipt(p)})};
count.text="$shown record${if(shown==1)"" else "s"}";
if(shown==0)list.addView(tx("No matching purchase records",14f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(30),0,dp(30))})}
val watcher=SimpleWatcher{draw()};search.addTextChangedListener(watcher);from.addTextChangedListener(watcher);to.addTextChangedListener(watcher);
val selection=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(a:AdapterView<*>?,v:View?,position:Int,id:Long)=draw();override fun onNothingSelected(a:AdapterView<*>?)=draw()};
farmer.onItemSelectedListener=selection;product.onItemSelectedListener=selection;draw()}
    private fun showDatePicker(target:EditText){val initial=try{LocalDate.parse(target.text.toString())}catch(e:Exception){LocalDate.now()};DatePickerDialog(this,{_,year,month,day->target.setText(LocalDate.of(year,month+1,day).toString())},initial.year,initial.monthValue-1,initial.dayOfMonth).show()}
    private fun purchaseCard(p:JSONObject,go:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;
setPadding(dp(15),dp(10),dp(15),dp(10));
setBackgroundColor(Color.WHITE);
addView(tx(find(farmers,p.getInt("farmerId")).optString("name"),16f,deep,true));
addView(tx("${p.optString("purchaseDate")} · ${p.optDouble("weight")} ${p.optString("unit")} · ${p.optString("status")}",13f,muted));
setOnClickListener{go()};
layoutParams=LinearLayout.LayoutParams(-1,dp(75)).apply{topMargin=dp(9)}}
    private fun find(a:JSONArray,id:Int):JSONObject{for(i in 0 until a.length())if(a.getJSONObject(i).getInt("id")==id)return a.getJSONObject(i);
return JSONObject()}
    inner class SignatureView:View(this){private val pen=Paint().apply{color=deep;
strokeWidth=7f;
style=Paint.Style.STROKE;
strokeCap=Paint.Cap.ROUND};
private val image=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888);
private val paper=Canvas(image).apply{drawColor(Color.WHITE)};
private var x=0f;
private var y=0f;
var empty=true;
init{setBackgroundColor(Color.WHITE)};
override fun onDraw(c:Canvas){c.drawBitmap(image,null,Rect(0,0,width,height),pen)};
override fun onTouchEvent(e:MotionEvent):Boolean{val xx=e.x*image.width/width;
val yy=e.y*image.height/height;
if(e.action==0){x=xx;
y=yy;
empty=false}else if(e.action==2){paper.drawLine(x,y,xx,yy,pen);
x=xx;
y=yy;
invalidate()};
return true};
fun clear(){paper.drawColor(Color.WHITE);
empty=true;
invalidate()};
fun dataUrl():String{val o=ByteArrayOutputStream();
image.compress(Bitmap.CompressFormat.PNG,100,o);
return "data:image/png;base64,"+Base64.encodeToString(o.toByteArray(),Base64.NO_WRAP)}}
}
class SimpleWatcher(private val fn:(String)->Unit):android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};
override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int)=fn(s.toString());
override fun afterTextChanged(e:android.text.Editable?){}}

internal fun matchesFarmerSearch(query:String,fields:List<String>):Boolean {
    val normalized=query.trim()
    return normalized.isEmpty()||fields.any{it.contains(normalized,ignoreCase=true)}
}

internal fun matchesPurchaseFilters(query:String,from:String,to:String,farmerFilter:Int?,productFilter:Int?,date:String,farmerId:Int,productId:Int,fields:List<String>):Boolean {
    val q=query.trim()
    return (q.isEmpty()||fields.any{it.contains(q,ignoreCase=true)}) &&
        (from.trim().isEmpty()||date>=from.trim()) && (to.trim().isEmpty()||date<=to.trim()) &&
        (farmerFilter==null||farmerId==farmerFilter) && (productFilter==null||productId==productFilter)
}
