using CiteWise_Web.Models;
using CiteWise_Web.Services;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllersWithViews();

//Add HttpClient support
builder.Services.AddHttpClient();

//For appsettings.json
//builder.Services.Configure<EmailSettings>(
//    builder.Configuration.GetSection("EmailSettings"));

// ---------- EmailSettings from ENV instead of appsettings.json ----------
builder.Services.Configure<EmailSettings>(options =>
{
    var provider = Environment.GetEnvironmentVariable("EMAIL_PROVIDER");
    var sender = Environment.GetEnvironmentVariable("EMAIL_SENDER_EMAIL");
    var senderName = Environment.GetEnvironmentVariable("EMAIL_SENDER_NAME");
    var user = Environment.GetEnvironmentVariable("EMAIL_SMTP_USERNAME");
    var pass = Environment.GetEnvironmentVariable("EMAIL_SMTP_PASSWORD");

    options.Provider = string.IsNullOrWhiteSpace(provider) ? "Gmail" : provider;
    options.SenderEmail = string.IsNullOrWhiteSpace(sender) ? "" : sender;
    options.SenderName = string.IsNullOrWhiteSpace(senderName) ? "CiteWise" : senderName;
    options.SmtpUsername = string.IsNullOrWhiteSpace(user) ? options.SenderEmail : user;
    options.SmtpPassword = string.IsNullOrWhiteSpace(pass) ? "" : pass;

    if (string.IsNullOrWhiteSpace(options.SenderEmail) ||
        string.IsNullOrWhiteSpace(options.SmtpUsername) ||
        string.IsNullOrWhiteSpace(options.SmtpPassword))
    {
        // DEMO MODE: don't crash the app if email isn't configured.
        Console.WriteLine(
            "WARNING: Email configuration is incomplete. " +
            "Email features may not work, but the site will run.");
    }
});

// -----------------------------------------------------------------------

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

if (File.Exists(firebasepath))
{
    FirebaseApp.Create(new AppOptions()
    {
        Credential = GoogleCredential.FromFile(firebasepath)
    });
}
else
{
    Console.WriteLine($"WARNING: Firebase key not found at {firebasepath}. Firebase will not be initialized.");
}

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