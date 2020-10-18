package com.example.redemption;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.example.redemption.R;



import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.startButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MoveToInfoPage();
            }
        });
    }

    public void MoveToInfoPage(){
        Intent intent = new Intent(MainActivity.this, Test.class);
        startActivity(intent);
    }
}
