package com.example.coffeecheckai.ui;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.ui.scan.ScanFragment;
import com.example.coffeecheckai.ui.history.HistoryFragment;
import com.example.coffeecheckai.ui.statistics.StatisticsFragment;
import com.example.coffeecheckai.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        setupListeners();

        // Mặc định mở tab Scan khi mở app
        replaceFragment(new ScanFragment());
    }

    private void findViews() {
        bottomNav = findViewById(R.id.bottomNav);
    }

    private void setupListeners() {
        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.nav_scan) {
                    replaceFragment(new ScanFragment());
                } else if (id == R.id.nav_history) {
                    replaceFragment(new HistoryFragment());
                } else if (id == R.id.nav_statistics) {
                    replaceFragment(new StatisticsFragment());
                } else if (id == R.id.nav_settings) {
                    replaceFragment(new SettingsFragment());
                }
                return true;
            }
        });
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}