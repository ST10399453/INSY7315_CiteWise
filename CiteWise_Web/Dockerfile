# =========================
# 1. Build stage
# =========================
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy csproj and restore as distinct layers
COPY ["CiteWise_Web.csproj", "./"]
RUN dotnet restore "CiteWise_Web.csproj"

# Copy the rest of the source and build
COPY . .
RUN dotnet publish "CiteWise_Web.csproj" -c Release -o /app/publish

# =========================
# 2. Runtime stage
# =========================
FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS final
WORKDIR /app

COPY --from=build /app/publish .

# Expose default ASP.NET port
EXPOSE 8080

# If your app listens on a different port, change ASPNETCORE_URLS
ENV ASPNETCORE_URLS=http://+:8080
ENV ASPNETCORE_ENVIRONMENT=Production

ENTRYPOINT ["dotnet", "CiteWise_Web.dll"]
