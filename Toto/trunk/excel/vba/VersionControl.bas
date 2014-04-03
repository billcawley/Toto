Attribute VB_Name = "VersionControl"

Private Declare Function URLDownloadToFile Lib "urlmon" Alias _
  "URLDownloadToFileA" (ByVal pCaller As Long, ByVal szURL As String, ByVal _
    szFileName As String, ByVal dwReserved As Long, ByVal lpfnCB As Long) As Long
    
Private Declare Function DeleteUrlCacheEntry Lib "wininet.dll" Alias "DeleteUrlCacheEntryA" (ByVal lpszUrlName As String) As Long

'these routines developed by Bill Cawley (bill@azquo.com) for updating our worksheets, but we are happy to share them, and would value any suggestions for improvement.
'it's based on ideas of others taken from the web.

'The security option in Excel which allows access to the VBA code needs to be set!

'This version control still requires the manual alteration of the version number in 'AzquoCommon.vba' and 'Version.txt'.
'Also the VersionControl.bas module is not itself replaced!

'to test it, load it into a blank workbook, and run 'UpdateCode'

'The Mac work has not been tested in this context.

#If Mac Then
Function getFile(FileName As String)
     getFile = HTTPPost(FileName, "")
End Function

#Else
  Function getFile(FileName As String)
    Set pXmlHttp = CreateObject("MSXML2.XMLHTTP")
    pXmlHttp.Open "POST", FileName, False
    pXmlHttp.SetRequestHeader "Content-Type", "application/x-www-form-urlencoded"
    pXmlHttp.Send ("")
    getFile = pXmlHttp.responseText
  End Function
#End If


Sub UpdateCode()
   Version = getFile("http://www.azquo.com/vba/Version.txt")
   If Left(Version, 7) <> "Version" Then
      MsgBox ("Cannot find a version file for updating")
      End
   End If
   dirPath$ = "c:\temp\"
   If Dir(dirPath$, vbDirectory) = "" Then
      MkDir dirPath$
   End If
   Files = Split(Version, vbCrLf)
   NewVersionNo = Mid(Files(0), 9, 1000)
   If NewVersionNo <= azVersion Then
      MsgBox ("No newer version available")
      End
    End If
    ZapModules
    LastFile = ""
    For fileno = 1 To UBound(Files)
       If Len(Files(fileno)) > 4 Then
          FileRoot = Left(Files(fileno), Len(Files(fileno)) - 4)
          If (FileRoot <> LastFile) Then
             If LastFile > "" Then
                ThisWorkbook.VBProject.VBComponents.Import dirPath$ & LastFile & ".vba"
             End If
             LastFile = FileRoot
          End If
          FiletoDownload = "http://www.azquo.com/vba/" & Files(fileno)
          DeleteUrlCacheEntry (FiletoDownload)
          Call URLDownloadToFile(0, FiletoDownload, dirPath$ & Files(fileno), 0, 0)
       End If
        
    Next
    If LastFile > "" Then
        ThisWorkbook.VBProject.VBComponents.Import dirPath$ & LastFile & ".vba"
    End If
    Kill dirPath$ & "*.*"
    RmDir dirPath$
   


End Sub



Sub SaveCodeModules()

'This code Exports all VBA modules
Dim i%, sName$

Dim dirPath$

dirPath$ = "G:\azquo\vba\"

With ThisWorkbook.VBProject
    For i% = 1 To .VBComponents.Count
        If .VBComponents(i%).CodeModule.CountOfLines > 0 Then
            sName$ = .VBComponents(i%).CodeModule.Name
            If Not isWorksheet(sName$) Then
               .VBComponents(i%).Export dirPath$ & sName$ & ".vba"
            End If
        End If
    Next i
End With

End Sub

Function isWorksheet(vbName)
  For Each Sheet In ActiveWorkbook.Sheets
    If Sheet.CodeName = vbName Then
      isWorksheet = True
      Exit Function
    End If
  Next
  isWorksheet = False
End Function

Sub ZapModules()

Dim dirPath$

dirPath$ = "F:\azquo\vba\"

On Error GoTo Noaccess
With ThisWorkbook.VBProject
    For i% = .VBComponents.Count To 1 Step -1
        If .VBComponents(i%).CodeModule.CountOfLines > 0 And .VBComponents(i%).Name <> "VersionControl" And Not isWorksheet(.VBComponents(i%).Name) Then
            .VBComponents.Remove .VBComponents(.VBComponents(i%).CodeModule.Name)
        End If
    Next i
End With

Exit Sub
Noaccess:
  MsgBox ("To update your Visual Basic code, you need to set the security options in Excel to trust the code to access the Visual Basic routines")
  End
End Sub
