package org.tribot.gradle.plugin;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.List;
import java.util.stream.Collectors;

// This will run in a new jvm - we use Java to make it as simple as possible to run (no kotlin deps)
public class LoginPrompt extends Application {

	private static final List<String> acceptedUrls = List.of(
			"https://community.tribot.org/login",
			"https://auth.tribot.org/authorize",
			"https://auth.tribot.org/u/login",
			"https://community.tribot.org/oauth/callback",
			"https://community.tribot.org/?&_mfaLogin=1"
	);

	@Override
	public void start(final Stage primaryStage) {
		final var manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(manager);
		final var view = new WebView();
		view.getEngine().javaScriptEnabledProperty().set(true);
		final var stage = new Stage();
		stage.setTitle("TRiBot Login");
		stage.setScene(new Scene(view));
		view.getEngine().getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
			if (newState == Worker.State.SUCCEEDED
			    && view.getEngine().getLocation().equals("https://community.tribot.org/login")) {
				view.getEngine().executeScript("document.getElementsByName('_processLogin')[0].click()");
			}
		});
		view.getEngine().locationProperty().addListener((obs, old, newv) -> {
			if (stage.isShowing() && acceptedUrls.stream().noneMatch(newv::startsWith)) {
				stage.close();
			}
		});
		view.getEngine().setOnError(e -> e.getException().printStackTrace());
		view.setPrefSize(550.0, 700.0);
		view.getEngine().load("https://community.tribot.org/login");
		stage.centerOnScreen();
		stage.showAndWait();
		if (manager.getCookieStore().getCookies().stream().map(HttpCookie::getName).anyMatch("ips4_loggedIn"::equals)) {
			final var cookies = manager.getCookieStore()
			                           .getCookies()
			                           .stream()
			                           .map(c -> c.getName() + ":" + c.getValue())
			                           .collect(Collectors.joining(";"));
			final var loginMultipleTimes = loginMultipleTimes();
			System.out.println("SaveLogin:" + loginMultipleTimes + ",Cookies:" + cookies);
		}
		System.exit(0);
	}

	private boolean loginMultipleTimes() {
		final var alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Login Type");
		alert.setHeaderText("Would you like to save your login?");
		alert.setContentText("If you are saving your login - make sure to keep your device secure." +
		                     "It will be saved at .tribot/settings/repo.dat.");

		final var loginOnce = new ButtonType("Login Once");
		final var saveLogin = new ButtonType("Save Login");

		alert.getButtonTypes().setAll(loginOnce, saveLogin);

		return alert.showAndWait()
				.map(b -> b == saveLogin)
				.orElseThrow();
	}

	public static void main(String[] args) {
		Application.launch(args);
	}

}
