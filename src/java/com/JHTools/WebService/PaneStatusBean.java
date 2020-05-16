package com.JHTools.WebService;

// Java
import java.io.Serializable;

// TBD
public class PaneStatusBean implements Serializable {

  public boolean isAlive() {
    return alive;
    }

  public void setAlive(boolean alive) {
    this.alive = alive;
    }  

  private boolean alive;

  }
