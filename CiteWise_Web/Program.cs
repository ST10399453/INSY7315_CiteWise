using CiteWise_Web.Services;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllersWithViews();

//Add HttpClient support
builder.Services.AddHttpClient();

//Register ApiService
builder.Services.AddScoped<ApiService>();

builder.Services.AddSingleton<FirebaseService>();

//// Add session support
builder.Services.AddDistributedMemoryCache(); // Required for session
builder.Services.AddSession(options =>
{
    options.IdleTimeout = TimeSpan.FromMinutes(30);
    options.Cookie.HttpOnly = true;
    options.Cookie.IsEssential = true;
});

var app = builder.Build();

var firebasePathFromEnv = Environment.GetEnvironmentVariable("FIREBASE_KEY_PATH");

var firebasepath = !string.IsNullOrEmpty(firebasePathFromEnv)
    ? firebasePathFromEnv
    : Path.Combine(app.Environment.ContentRootPath, "FirebaseKey", "citewise_two.json");

if (!File.Exists(firebasepath))
{
    throw new FileNotFoundException($"Firebase key not found {firebasepath}");
}

FirebaseApp.Create(new AppOptions()
{
    Credential = GoogleCredential.FromFile(firebasepath)
});

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}



app.UseHttpsRedirection();
app.UseRouting();
app.UseSession();
app.UseAuthentication();
app.UseAuthorization();

app.MapStaticAssets();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");
//.WithStaticAssets();
app.Run();