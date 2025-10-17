// ✅ CiteWise Firebase configuration
const firebaseConfig = {
    apiKey: "AIzaSyBxHbq9jXn4KRWmvMmUNeskFEvWsD17JxA",
    authDomain: "budgetapp-fbcbf.firebaseapp.com",
    projectId: "budgetapp-fbcbf",
    databaseURL: "https://budgetapp-fbcbf-default-rtdb.firebaseio.com",
};

// ✅ Initialize Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();

// ✅ Google Sign-In Button Click
document.getElementById("btnGoogleLogin").addEventListener("click", () => {
    const provider = new firebase.auth.GoogleAuthProvider();

    // Request user’s display name and email
    provider.addScope("profile");
    provider.addScope("email");

    // Start popup login
    auth.signInWithPopup(provider)
        .then(result => {   
            // Get the user's ID token (JWT)
            return result.user.getIdToken();
        })
        .then(idToken => {
            // Send token to backend for verification
            return fetch('/Account/GoogleLogin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: idToken })
            });
        })
        .then(async res => {
            if (!res.ok) {
                const errorData = await res.json();
                throw new Error(errorData.message || "Login failed on server.");
            }

            // Extract redirect URL returned from controller
            const data = await res.json();

            if (data.success && data.redirectUrl) {
                window.location.href = data.redirectUrl;
            } else {
                alert("Login failed: No redirect URL received.");
            }
        })
        .catch(error => {
            console.error("Google Login Error:", error);
            alert("Login failed: " + error.message);
        });
});
