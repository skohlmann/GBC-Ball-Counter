package de.speexx.lego.gbc.ballcounter;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

public class GovernanceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_governance);

        final TextView htmlTextView = (TextView)findViewById(R.id.core_license_text_view);
        htmlTextView.setText(Html.fromHtml(getString(R.string.license_text)));
    }
}
