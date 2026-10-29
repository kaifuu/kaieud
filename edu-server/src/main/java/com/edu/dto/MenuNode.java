package com.edu.dto;

import com.edu.entity.SysMenu;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MenuNode extends SysMenu {
    private List<MenuNode> children;
}
